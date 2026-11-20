-- =====================================================================
-- ThermoCity - Priorização da busca ativa no banco (PostgreSQL / PL/pgSQL)
--
-- Regra de prioridade v1 (a mesma que estava em PrioridadeService.java):
--   alcance por nível ......... NC3: 70+ em setores IPVS 5-6 | NC4: 60+ em IPVS 4-6 | NC5: todos os 60+
--   pontos .................... mora sozinho 3 · mobilidade reduzida 2 · condição crônica 2 · 70+ 2
--                               · (nota de calor do setor - 1) x 1 · IPVS 5-6 2 / IPVS 4 1
--                               · refúgio a mais de 800 m (ou nenhum) 1
--   ordem ..................... pontuação desc, idade desc, id da pessoa
--   motivos ................... "mora sozinha · condição crônica · 78 anos · setor nota 5 · IPVS 5"
--
-- Objetos:
--   tmc_prc_gerar_lista_busca_ativa ... monta/atualiza a lista de cada UBS para um dia e nível
--   tmc_fn_trg_reorganizar_lista ...... função da trigger: reorganiza as listas abertas de hoje
--   tmc_trg_pessoa_reorganiza_lista ... trigger AFTER INSERT OR UPDATE em tmc_pessoa_sintetica
--
-- Pode rodar de novo (CREATE OR REPLACE / DROP TRIGGER IF EXISTS).
-- Requisito: tabelas de scripts/script-bd.sql já criadas.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. PROCEDURE: gera ou atualiza as listas do dia
--    Regras (iguais às da versão Java):
--    - uma lista por UBS válida por dia; se já existe, é reaberta e o nível só sobe;
--    - quem entra no alcance é incluído como PENDENTE;
--    - quem já estava na lista e saiu do alcance continua, com a pontuação antiga;
--    - o status dos contatos já feitos nunca é alterado;
--    - as posições são renumeradas pela pontuação a cada chamada.
-- ---------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE tmc_prc_gerar_lista_busca_ativa(
    IN    p_data                  DATE,
    IN    p_nivel                 INTEGER,
    IN    p_evento_id             VARCHAR,
    INOUT p_listas_afetadas       INTEGER DEFAULT NULL,
    INOUT p_pessoas_incluidas     INTEGER DEFAULT NULL,
    INOUT p_total_nas_listas      INTEGER DEFAULT NULL,
    INOUT p_sem_cadastro_completo INTEGER DEFAULT NULL,
    IN    p_ubs_excluidas         VARCHAR[] DEFAULT ARRAY['CNES-8016305', 'CNES-8016291', 'CNES-118796'],
    IN    p_versao_regra          VARCHAR DEFAULT 'v1'
)
LANGUAGE plpgsql
AS $$
DECLARE
    -- pesos da regra v1
    c_peso_70_mais      CONSTANT INTEGER := 2;
    c_peso_mora_sozinho CONSTANT INTEGER := 3;
    c_peso_mobilidade   CONSTANT INTEGER := 2;
    c_peso_cronica      CONSTANT INTEGER := 2;
    c_peso_nota_calor   CONSTANT INTEGER := 1;   -- x (nota - 1)
    c_peso_ipvs_5_6     CONSTANT INTEGER := 2;
    c_peso_ipvs_4       CONSTANT INTEGER := 1;
    c_peso_refugio      CONSTANT INTEGER := 1;
    c_distancia_refugio CONSTANT NUMERIC := 800;  -- metros
    c_deslocamento      CONSTANT INTEGER := 1000000;

    v_idade_minima INTEGER;
    v_ipvs_minimo  INTEGER;
BEGIN
    IF p_nivel IS NULL OR p_nivel NOT BETWEEN 3 AND 5 THEN
        RAISE EXCEPTION 'Nível sem busca ativa: NC% (a lista começa no NC3)', p_nivel;
    END IF;

    -- alcance do nível
    v_idade_minima := CASE p_nivel WHEN 3 THEN 70 ELSE 60 END;
    v_ipvs_minimo  := CASE p_nivel WHEN 3 THEN 5 WHEN 4 THEN 4 ELSE 0 END;

    -- UBS que podem ser referência (sem penitenciária, triagem COVID etc.)
    -- tabelas temporárias da chamada (somem no fim da transação; a trigger pode chamar de novo na mesma)
    IF to_regclass('pg_temp.tmp_ubs_validas') IS NULL THEN
        CREATE TEMP TABLE tmp_ubs_validas (id VARCHAR(40) PRIMARY KEY) ON COMMIT DROP;
    END IF;
    TRUNCATE tmp_ubs_validas;
    INSERT INTO tmp_ubs_validas (id)
    SELECT s.id
      FROM tmc_servico s
     WHERE s.tipo = 'UBS'
       AND s.lat IS NOT NULL AND s.lon IS NOT NULL
       AND s.id <> ALL (coalesce(p_ubs_excluidas, ARRAY[]::VARCHAR[]));

    -- pessoas sem setor ou sem UBS válida ficam de fora (o cadastro precisa ser corrigido)
    SELECT count(*)
      INTO p_sem_cadastro_completo
      FROM tmc_pessoa_sintetica p
     WHERE p.setor_codigo IS NULL
        OR p.ubs_ref_id IS NULL
        OR p.ubs_ref_id NOT IN (SELECT id FROM tmp_ubs_validas);

    -- candidatos do nível, com pontuação e motivos
    IF to_regclass('pg_temp.tmp_candidatos') IS NULL THEN
        CREATE TEMP TABLE tmp_candidatos (
            ubs_id       VARCHAR(40),
            pessoa_id    BIGINT,
            setor_codigo VARCHAR(15),
            pontuacao    INTEGER,
            motivos      VARCHAR(500),
            idade        INTEGER
        ) ON COMMIT DROP;
    END IF;
    TRUNCATE tmp_candidatos;

    INSERT INTO tmp_candidatos (ubs_id, pessoa_id, setor_codigo, pontuacao, motivos, idade)
    WITH base AS (
        SELECT p.id,
               p.ubs_ref_id,
               p.setor_codigo,
               p.mora_sozinho,
               p.mobilidade_reduzida,
               p.condicao_cronica,
               upper(coalesce(p.sexo, '')) = 'F' AS feminino,
               p.lat,
               p.lon,
               -- idade na data da lista: nascimento, senão a idade gravada, senão a faixa etária
               coalesce(extract(YEAR FROM age(p_data, p.data_nascimento))::INTEGER,
                        p.idade,
                        CASE WHEN p.faixa_etaria = '70+' THEN 70 ELSE 60 END) AS idade,
               st.ipvs_grupo,
               cs.classe_calor AS nota
          FROM tmc_pessoa_sintetica p
          JOIN tmc_setor st ON st.codigo = p.setor_codigo
          JOIN tmp_ubs_validas u ON u.id = p.ubs_ref_id
          LEFT JOIN tmc_calor_setor cs ON cs.codigo_setor = p.setor_codigo
    ),
    alcance AS (
        SELECT b.*,
               -- refúgio mais próximo (Haversine), só os revisados como manter/potencial
               (SELECT min(2 * 6371000 * asin(sqrt(
                            power(sin(radians(r.lat - b.lat) / 2), 2)
                          + cos(radians(b.lat)) * cos(radians(r.lat))
                          * power(sin(radians(r.lon - b.lon) / 2), 2))))
                  FROM tmc_servico r
                 WHERE r.tipo = 'REFUGIO'
                   AND r.revisao IN ('manter', 'potencial')
                   AND r.lat IS NOT NULL AND r.lon IS NOT NULL) AS dist_refugio
          FROM base b
         WHERE b.idade >= v_idade_minima
           AND (v_ipvs_minimo <= 0 OR (b.ipvs_grupo IS NOT NULL AND b.ipvs_grupo >= v_ipvs_minimo))
    ),
    regra AS (
        SELECT a.*,
               (a.lat IS NOT NULL AND a.lon IS NOT NULL
                AND (a.dist_refugio IS NULL OR a.dist_refugio > c_distancia_refugio)) AS longe_refugio
          FROM alcance a
    )
    SELECT r.ubs_ref_id,
           r.id,
           r.setor_codigo,
             CASE WHEN r.mora_sozinho        THEN c_peso_mora_sozinho ELSE 0 END
           + CASE WHEN r.mobilidade_reduzida THEN c_peso_mobilidade   ELSE 0 END
           + CASE WHEN r.condicao_cronica    THEN c_peso_cronica      ELSE 0 END
           + CASE WHEN r.idade >= 70         THEN c_peso_70_mais      ELSE 0 END
           + greatest(0, coalesce(r.nota, 1) - 1) * c_peso_nota_calor
           + CASE WHEN r.ipvs_grupo >= 5 THEN c_peso_ipvs_5_6
                  WHEN r.ipvs_grupo = 4  THEN c_peso_ipvs_4 ELSE 0 END
           + CASE WHEN r.longe_refugio THEN c_peso_refugio ELSE 0 END,
           left(concat_ws(' · ',
                CASE WHEN r.mora_sozinho THEN CASE WHEN r.feminino THEN 'mora sozinha' ELSE 'mora sozinho' END END,
                CASE WHEN r.mobilidade_reduzida THEN 'mobilidade reduzida' END,
                CASE WHEN r.condicao_cronica THEN 'condição crônica' END,
                r.idade || ' anos',
                CASE WHEN r.nota IS NOT NULL THEN 'setor nota ' || r.nota END,
                CASE WHEN r.ipvs_grupo >= 5 THEN 'IPVS ' || r.ipvs_grupo
                     WHEN r.ipvs_grupo = 4  THEN 'IPVS 4' END,
                CASE WHEN r.longe_refugio AND r.dist_refugio IS NULL THEN 'sem refúgio cadastrado'
                     WHEN r.longe_refugio THEN 'refúgio a ' || replace(to_char(r.dist_refugio / 1000, 'FM990.0'), '.', ',') || ' km'
                END), 500),
           r.idade
      FROM regra r;

    -- uma lista por UBS válida: cria, ou reabre e só eleva o nível
    INSERT INTO tmc_lista_busca_ativa (data_lista, ubs_id, nivel_calor, evento_id, versao_regra, status, criada_em)
    SELECT p_data, u.id, p_nivel, p_evento_id, p_versao_regra, 'ABERTA', now()
      FROM tmp_ubs_validas u
    ON CONFLICT (data_lista, ubs_id) DO UPDATE
       SET status       = 'ABERTA',
           encerrada_em = NULL,
           nivel_calor  = greatest(tmc_lista_busca_ativa.nivel_calor, EXCLUDED.nivel_calor);

    GET DIAGNOSTICS p_listas_afetadas = ROW_COUNT;

    -- afasta as posições atuais para renumerar sem violar (lista, posição)
    UPDATE tmc_lista_item i
       SET posicao = i.posicao + c_deslocamento
      FROM tmc_lista_busca_ativa l
     WHERE l.id = i.lista_id
       AND l.data_lista = p_data
       AND i.posicao < c_deslocamento;

    -- quem já estava e continua no alcance: pontuação e motivos atualizados (status preservado)
    UPDATE tmc_lista_item i
       SET pontuacao = c.pontuacao,
           motivos   = c.motivos
      FROM tmp_candidatos c
      JOIN tmc_lista_busca_ativa l ON l.ubs_id = c.ubs_id AND l.data_lista = p_data
     WHERE i.lista_id = l.id
       AND i.pessoa_id = c.pessoa_id;

    -- quem entrou no alcance agora: PENDENTE (posição provisória, renumerada logo abaixo)
    INSERT INTO tmc_lista_item (lista_id, pessoa_id, setor_codigo, posicao, pontuacao, motivos, status_contato)
    SELECT l.id, c.pessoa_id, c.setor_codigo,
           2 * c_deslocamento + row_number() OVER (PARTITION BY l.id ORDER BY c.pessoa_id),
           c.pontuacao, c.motivos, 'PENDENTE'
      FROM tmp_candidatos c
      JOIN tmc_lista_busca_ativa l ON l.ubs_id = c.ubs_id AND l.data_lista = p_data
     WHERE NOT EXISTS (SELECT 1 FROM tmc_lista_item i WHERE i.lista_id = l.id AND i.pessoa_id = c.pessoa_id);

    GET DIAGNOSTICS p_pessoas_incluidas = ROW_COUNT;

    -- renumera cada lista: pontuação desc, idade desc (quem saiu do alcance conta como 0), id da pessoa
    WITH ordem AS (
        SELECT i.id,
               row_number() OVER (PARTITION BY i.lista_id
                                  ORDER BY i.pontuacao DESC, coalesce(c.idade, 0) DESC, i.pessoa_id NULLS LAST) AS nova
          FROM tmc_lista_item i
          JOIN tmc_lista_busca_ativa l ON l.id = i.lista_id AND l.data_lista = p_data
          LEFT JOIN tmp_candidatos c ON c.pessoa_id = i.pessoa_id AND c.ubs_id = l.ubs_id
    )
    UPDATE tmc_lista_item i
       SET posicao = o.nova
      FROM ordem o
     WHERE o.id = i.id;

    SELECT count(*)
      INTO p_total_nas_listas
      FROM tmc_lista_item i
      JOIN tmc_lista_busca_ativa l ON l.id = i.lista_id
     WHERE l.data_lista = p_data;
END;
$$;

COMMENT ON PROCEDURE tmc_prc_gerar_lista_busca_ativa(DATE, INTEGER, VARCHAR, INTEGER, INTEGER, INTEGER, INTEGER, VARCHAR[], VARCHAR)
    IS 'Gera ou atualiza a lista priorizada de busca ativa de cada UBS para o dia e o nível (regra v1).';

-- ---------------------------------------------------------------------
-- 2. TRIGGER: cadastro de pessoa mudou -> reorganiza as listas ABERTAS de hoje
--    FOR EACH STATEMENT: uma importação de 5.000 pessoas recalcula uma vez, não 5.000.
--    Sem lista aberta hoje (dia abaixo de NC3) não faz nada.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION tmc_fn_trg_reorganizar_lista()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_hoje        DATE := (now() AT TIME ZONE 'America/Sao_Paulo')::DATE;
    v_nivel       INTEGER;
    -- o PL/pgSQL exige variáveis para os parâmetros INOUT da procedure
    v_listas      INTEGER;
    v_incluidas   INTEGER;
    v_total       INTEGER;
    v_incompletos INTEGER;
BEGIN
    SELECT max(nivel_calor)
      INTO v_nivel
      FROM tmc_lista_busca_ativa
     WHERE data_lista = v_hoje
       AND status = 'ABERTA';

    IF v_nivel IS NOT NULL THEN
        CALL tmc_prc_gerar_lista_busca_ativa(v_hoje, v_nivel, 'TRIGGER-PESSOA',
                                             v_listas, v_incluidas, v_total, v_incompletos);
        RAISE NOTICE 'Listas de % reorganizadas (NC%): % pessoas incluídas, % no total',
            v_hoje, v_nivel, v_incluidas, v_total;
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS tmc_trg_pessoa_reorganiza_lista ON tmc_pessoa_sintetica;

CREATE TRIGGER tmc_trg_pessoa_reorganiza_lista
    AFTER INSERT OR UPDATE ON tmc_pessoa_sintetica
    FOR EACH STATEMENT
    EXECUTE FUNCTION tmc_fn_trg_reorganizar_lista();

COMMIT;

-- ---------------------------------------------------------------------
-- Testes rápidos (rodar à parte):
--   CALL tmc_prc_gerar_lista_busca_ativa(current_date, 4, 'TESTE-MANUAL');
--   SELECT l.ubs_id, i.posicao, i.pontuacao, i.motivos, i.status_contato
--     FROM tmc_lista_item i JOIN tmc_lista_busca_ativa l ON l.id = i.lista_id
--    WHERE l.data_lista = current_date ORDER BY l.ubs_id, i.posicao LIMIT 20;
--   UPDATE tmc_pessoa_sintetica SET mora_sozinho = true WHERE id = <id>;   -- dispara a trigger
-- ---------------------------------------------------------------------
