package br.com.fiap.ThermoCity.busca;

import br.com.fiap.ThermoCity.busca.mensageria.EventoNivelCalorHandler;
import br.com.fiap.ThermoCity.busca.model.AcaoEvento;
import br.com.fiap.ThermoCity.busca.service.ResultadoProcessamento;
import br.com.fiap.ThermoCity.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pessoas do cenário (setor A: IPVS 5, nota de calor 5; setor B: IPVS 3, nota 2):
 * - Dona Aparecida, 78, mora sozinha e condição crônica, setor A → 3 + 2 + 2 + 4 + 2 = 13
 * - Seu Benedito, 72, sem vulnerabilidades, setor A → 2 + 4 + 2 = 8
 * - Dona Cida, 65, mora sozinha, mobilidade reduzida e crônica, setor A → só a partir de NC4: 13
 * - Seu Jorge, 80, mora sozinho, setor B, refúgio válido a ~2,1 km → só no NC5: 3 + 2 + 1 + 1 = 7
 * - Dona Sem UBS, 75, setor A, sem UBS → fica fora e é contada para correção do cadastro
 */
class ListaControllerTest extends ApiTestBase {

    private static final long APARECIDA = 900001;
    private static final long BENEDITO = 900002;
    private static final long CIDA = 900003;
    private static final long JORGE = 900004;

    @Autowired
    private EventoNivelCalorHandler handler;

    @BeforeEach
    void pessoas() {
        inserirPessoaSintetica(APARECIDA, "Dona Aparecida", 78, "F", "SC", SETOR_A, UBS_A, -22.12, -51.385);
        inserirPessoaSintetica(BENEDITO, "Seu Benedito", 72, "M", "", SETOR_A, UBS_A, -22.121, -51.386);
        inserirPessoaSintetica(CIDA, "Dona Cida", 65, "F", "SMC", SETOR_A, UBS_A, -22.119, -51.384);
        inserirPessoaSintetica(JORGE, "Seu Jorge", 80, "M", "S", SETOR_B, UBS_B, -22.12, -51.37);
        inserirPessoaSintetica(900005, "Dona Sem UBS", 75, "F", "S", SETOR_A, null, -22.12, -51.385);
    }

    private ResultActions simular(int nivel) throws Exception {
        return simular(nivel, null);
    }

    private ResultActions simular(int nivel, LocalDate data) throws Exception {
        String corpo = data == null ? "{\"nivelNovo\":%d}".formatted(nivel)
                : "{\"nivelNovo\":%d,\"dataLocal\":\"%s\"}".formatted(nivel, data);
        return mockMvc.perform(post("/simulacoes/nivel-calor").header("Authorization", bearer(gestor))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static String evento(String id, Integer nivel) {
        return """
                {"idEvento":"%s","tipo":"NIVEL_CALOR_ALTERADO","dataLocal":"%s","nivelAnterior":2,
                 "nivelNovo":%s,"icMaxC":38.4,"origem":"PREVISTO","ocorridoEm":"2026-11-15T06:00:00-03:00",
                 "campoNovoDoDotNet":"ignorado"}""".formatted(id, hoje(), nivel);
    }

    // ---------- geração da lista (regra v1) ----------

    @Test
    void nc3GeraListaPorUbsEmOrdemDePontuacaoComMotivos() throws Exception {
        simular(3).andExpect(status().isOk())
                .andExpect(jsonPath("$.acao").value("LISTAS_GERADAS"))
                .andExpect(jsonPath("$.listasAfetadas").value(2))
                .andExpect(jsonPath("$.pessoasIncluidas").value(2))
                .andExpect(jsonPath("$.pessoasSemCadastroCompleto").value(1));

        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubsId").value(UBS_A))
                .andExpect(jsonPath("$.nivelCalor").value(3))
                .andExpect(jsonPath("$.versaoRegra").value("v1"))
                .andExpect(jsonPath("$.itens.totalElementos").value(2))
                .andExpect(jsonPath("$.itens.conteudo[0].posicao").value(1))
                .andExpect(jsonPath("$.itens.conteudo[0].pontuacao").value(13))
                .andExpect(jsonPath("$.itens.conteudo[0].pessoa.nome").value("Dona Aparecida"))
                .andExpect(jsonPath("$.itens.conteudo[0].motivos")
                        .value("mora sozinha · condição crônica · 78 anos · setor nota 5 · IPVS 5"))
                .andExpect(jsonPath("$.itens.conteudo[0].statusContato").value("PENDENTE"))
                .andExpect(jsonPath("$.itens.conteudo[0].refugioMaisProximo.nome").value("Biblioteca Municipal"))
                .andExpect(jsonPath("$.itens.conteudo[0].refugioMaisProximo.distanciaMetros",
                        allOf(greaterThan(450), lessThan(600))))
                .andExpect(jsonPath("$.itens.conteudo[1].pessoa.nome").value("Seu Benedito"))
                .andExpect(jsonPath("$.itens.conteudo[1].pontuacao").value(8))
                .andExpect(jsonPath("$.itens.conteudo[1].motivos").value("72 anos · setor nota 5 · IPVS 5"));
    }

    @Test
    void alcanceSegueONivel() throws Exception {
        simular(3);
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.totalElementos").value(0));

        simular(4).andExpect(jsonPath("$.pessoasIncluidas").value(1));
        // Dona Cida (65) entra com 13 pontos; empata com Dona Aparecida, que fica antes por ser mais velha
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(jsonPath("$.nivelCalor").value(4))
                .andExpect(jsonPath("$.itens.totalElementos").value(3))
                .andExpect(jsonPath("$.itens.conteudo[0].pessoa.nome").value("Dona Aparecida"))
                .andExpect(jsonPath("$.itens.conteudo[1].pessoa.nome").value("Dona Cida"))
                .andExpect(jsonPath("$.itens.conteudo[2].pessoa.nome").value("Seu Benedito"));

        simular(5);
        // IPVS 3 só entra no NC5; o refúgio a 2,1 km soma ponto (o refúgio "remover" ao lado é ignorado)
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteB)))
                .andExpect(jsonPath("$.itens.totalElementos").value(1))
                .andExpect(jsonPath("$.itens.conteudo[0].pontuacao").value(7))
                .andExpect(jsonPath("$.itens.conteudo[0].motivos")
                        .value("mora sozinho · 80 anos · setor nota 2 · refúgio a 2,1 km"));
    }

    @Test
    void nivelAbaixoDe3EncerraEVoltarAoCalorPreservaOsContatos() throws Exception {
        simular(3);
        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pessoaId\":%d,\"resultado\":\"PRECISA_AJUDA\"}".formatted(APARECIDA)))
                .andExpect(status().isCreated());

        simular(2).andExpect(jsonPath("$.acao").value("LISTAS_ENCERRADAS"))
                .andExpect(jsonPath("$.listasAfetadas").value(2));
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(status().isNoContent());

        simular(4);
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.totalElementos").value(3))
                .andExpect(jsonPath("$.itens.conteudo[0].pessoa.nome").value("Dona Aparecida"))
                .andExpect(jsonPath("$.itens.conteudo[0].statusContato").value("PRECISA_AJUDA"))
                .andExpect(jsonPath("$.itens.conteudo[0].ultimoContatoEm").isNotEmpty());
        assertThat(contar("tmc_lista_busca_ativa")).isEqualTo(2);
    }

    @Test
    void contatoRegistradoApareceNaLista() throws Exception {
        simular(3);
        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pessoaId\":%d,\"resultado\":\"CONTATADO_BEM\"}".formatted(BENEDITO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listaItemId").isNumber());

        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(jsonPath("$.itens.conteudo[1].statusContato").value("CONTATADO_BEM"));
    }

    @Test
    void listaDeOutroDiaFicaDisponivelPorData() throws Exception {
        LocalDate amanha = hoje().plusDays(1);
        simular(3, amanha).andExpect(status().isOk());

        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/listas").param("data", amanha.toString()).header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(amanha.toString()))
                .andExpect(jsonPath("$.itens.totalElementos").value(2));
    }

    // ---------- consulta: papéis e paginação ----------

    @Test
    void semListaNoDiaResponde204() throws Exception {
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(status().isNoContent());
    }

    @Test
    void agenteNaoVeListaDeOutraUbs() throws Exception {
        simular(3);
        mockMvc.perform(get("/listas/hoje").param("ubs", UBS_B).header("Authorization", bearer(agenteA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("O agente só consulta a lista da sua UBS"));
    }

    @Test
    void cidadaoNaoVeListas() throws Exception {
        simular(3);
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(cidadao)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorVeTodasAsListasOuUmaUbs() throws Exception {
        simular(5);
        mockMvc.perform(get("/listas/hoje").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listas").value(2))
                .andExpect(jsonPath("$.ubsId").doesNotExist())
                .andExpect(jsonPath("$.itens.totalElementos").value(4));

        mockMvc.perform(get("/listas/hoje").param("ubs", UBS_B).param("size", "1")
                        .header("Authorization", bearer(gestor)))
                .andExpect(jsonPath("$.ubsId").value(UBS_B))
                .andExpect(jsonPath("$.itens.totalElementos").value(1))
                .andExpect(jsonPath("$.itens.conteudo[0].pessoa.nome").value("Seu Jorge"));
    }

    @Test
    void paginacaoDaLista() throws Exception {
        simular(4);
        mockMvc.perform(get("/listas/hoje").param("page", "1").param("size", "2")
                        .header("Authorization", bearer(agenteA)))
                .andExpect(jsonPath("$.itens.pagina").value(1))
                .andExpect(jsonPath("$.itens.totalPaginas").value(2))
                .andExpect(jsonPath("$.itens.conteudo.length()").value(1))
                .andExpect(jsonPath("$.itens.conteudo[0].posicao").value(3));
        mockMvc.perform(get("/listas/hoje").param("size", "0").header("Authorization", bearer(agenteA)))
                .andExpect(status().isBadRequest());
    }

    // ---------- simulação ----------

    @Test
    void simulacaoSoParaGestorEComNivelValido() throws Exception {
        mockMvc.perform(post("/simulacoes/nivel-calor").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nivelNovo\":3}"))
                .andExpect(status().isForbidden());
        simular(7).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]").value("nivelNovo: O nível deve estar entre 1 e 5"));
    }

    // ---------- evento do RabbitMQ ----------

    @Test
    void eventoDaFilaGeraListaEEventoRepetidoEIgnorado() {
        ResultadoProcessamento primeiro = handler.tratar(evento("evt-123", 3));
        assertThat(primeiro.acao()).isEqualTo(AcaoEvento.LISTAS_GERADAS);
        assertThat(primeiro.totalNasListas()).isEqualTo(2);

        ResultadoProcessamento repetido = handler.tratar(evento("evt-123", 3));
        assertThat(repetido.acao()).isEqualTo(AcaoEvento.DUPLICADO);
        assertThat(contar("tmc_lista_busca_ativa")).isEqualTo(2);
        assertThat(contar("tmc_lista_item")).isEqualTo(2);
        assertThat(contar("tmc_evento_processado")).isEqualTo(1);
    }

    @Test
    void eventoSemListaAbertaENivelBaixoNaoFazNada() {
        assertThat(handler.tratar(evento("evt-nc2", 2)).acao()).isEqualTo(AcaoEvento.SEM_ACAO);
        assertThat(contar("tmc_lista_busca_ativa")).isZero();
    }

    @Test
    void mensagemInvalidaVaiParaAFilaDeErro() {
        assertThatThrownBy(() -> handler.tratar(evento("evt-sem-nivel", null)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("nivelNovo ausente");
        assertThatThrownBy(() -> handler.tratar("{isso não é json"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("JSON inválido");
        assertThatThrownBy(() -> handler.tratar(evento("evt-nc9", 9)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(contar("tmc_evento_processado")).isZero();
        assertThat(contar("tmc_lista_busca_ativa")).isZero();
    }
}
