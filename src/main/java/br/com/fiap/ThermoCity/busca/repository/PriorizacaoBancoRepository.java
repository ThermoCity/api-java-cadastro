package br.com.fiap.ThermoCity.busca.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Chama a procedure tmc_prc_gerar_lista_busca_ativa (scripts/script-priorizacao.sql), que aplica a regra de
 * prioridade v1 no banco e monta ou atualiza a lista de cada UBS para o dia e o nível de calor.
 * A mesma procedure é chamada pela trigger tmc_trg_pessoa_reorganiza_lista quando o cadastro de pessoas muda.
 */
@Repository
public class PriorizacaoBancoRepository {

    private static final String CHAMADA = "CALL tmc_prc_gerar_lista_busca_ativa(?, ?, ?::varchar, NULL, NULL, NULL, NULL, "
            + "string_to_array(?, ',')::varchar[], ?::varchar)";

    private final JdbcTemplate jdbc;

    public PriorizacaoBancoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Resultado gerar(LocalDate data, int nivel, String eventoId, Collection<String> ubsExcluidas,
                           String versaoRegra) {
        Map<String, Object> saida = jdbc.queryForMap(CHAMADA, data, nivel, eventoId,
                String.join(",", ubsExcluidas), versaoRegra);
        return new Resultado(inteiro(saida.get("p_listas_afetadas")), inteiro(saida.get("p_pessoas_incluidas")),
                inteiro(saida.get("p_total_nas_listas")), inteiro(saida.get("p_sem_cadastro_completo")));
    }

    private static int inteiro(Object valor) {
        return valor instanceof Number n ? n.intValue() : 0;
    }

    /** Contagens devolvidas pela procedure (parâmetros INOUT). */
    public record Resultado(int listasAfetadas, int pessoasIncluidas, int totalNasListas, int semCadastroCompleto) {
    }
}
