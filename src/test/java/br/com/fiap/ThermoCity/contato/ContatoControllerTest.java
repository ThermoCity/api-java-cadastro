package br.com.fiap.ThermoCity.contato;

import br.com.fiap.ThermoCity.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContatoControllerTest extends ApiTestBase {

    private static String contatoJson(long pessoaId, String resultado) {
        return "{\"pessoaId\":%d,\"resultado\":\"%s\",\"observacao\":\"obs\"}".formatted(pessoaId, resultado);
    }

    private long criarLista(LocalDate data, String ubsId) {
        jdbc.update("insert into tmc_lista_busca_ativa (data_lista, ubs_id, nivel_calor, evento_id, versao_regra, "
                + "status, criada_em) values (?, ?, 4, ?, 'v1', 'ABERTA', current_timestamp)",
                data, ubsId, "evt-" + ubsId + data);
        return jdbc.queryForObject("select max(id) from tmc_lista_busca_ativa", Long.class);
    }

    private void incluirNaLista(long listaId, long pessoaId, int posicao, String setor, String status) {
        jdbc.update("insert into tmc_lista_item (lista_id, pessoa_id, setor_codigo, posicao, pontuacao, motivos, "
                + "status_contato) values (?, ?, ?, ?, 5, 'mora sozinha · 78 anos', ?)",
                listaId, pessoaId, setor, posicao, status);
    }

    private String statusNaLista(long pessoaId) {
        return jdbc.queryForObject("select status_contato from tmc_lista_item where pessoa_id = ?",
                String.class, pessoaId);
    }

    // ---------- POST /contatos ----------

    @Test
    void contatoRegistradoAtualizaASituacaoNaListaDoDia() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
        incluirNaLista(criarLista(hoje(), UBS_A), pessoa, 1, SETOR_A, "PENDENTE");

        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, "CONTATADO_BEM")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/contatos/")))
                .andExpect(jsonPath("$.pessoaId").value(pessoa))
                .andExpect(jsonPath("$.resultado").value("CONTATADO_BEM"))
                .andExpect(jsonPath("$.resultadoDescricao").value("Contatado e bem"))
                .andExpect(jsonPath("$.destaque").value(false))
                .andExpect(jsonPath("$.agenteId").value(agenteA.getIdUsuario()))
                .andExpect(jsonPath("$.ubsId").value(UBS_A))
                .andExpect(jsonPath("$.listaItemId").isNumber());

        assertThat(statusNaLista(pessoa)).isEqualTo("CONTATADO_BEM");
    }

    @Test
    void contatoForaDeListaAbertaEGravadoSemItem() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
        incluirNaLista(criarLista(hoje().minusDays(1), UBS_A), pessoa, 1, SETOR_A, "PENDENTE");

        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, "nao atendeu")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("NAO_ATENDEU"))
                .andExpect(jsonPath("$.listaItemId").doesNotExist());

        assertThat(statusNaLista(pessoa)).isEqualTo("PENDENTE");
    }

    @Test
    void precisaDeAjudaFicaDestacadoParaOGestor() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
        long outra = cadastrarPessoa(cidadao, pessoaJson("Seu Benedito"));
        long lista = criarLista(hoje(), UBS_A);
        incluirNaLista(lista, pessoa, 1, SETOR_A, "PENDENTE");
        incluirNaLista(lista, outra, 2, SETOR_A, "PENDENTE");

        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, "PRECISA_AJUDA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destaque").value(true));
        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(outra, "CONTATADO_BEM")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/contatos?resultado=PRECISA_AJUDA").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].pessoaNome").value("Dona Aparecida"))
                .andExpect(jsonPath("$.conteudo[0].destaque").value(true));
        assertThat(statusNaLista(pessoa)).isEqualTo("PRECISA_AJUDA");
    }

    @Test
    void resultadoInvalidoResponde422ComOsValidos() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));

        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, "TALVEZ")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.mensagem").value("Resultado de contato inválido"))
                .andExpect(jsonPath("$.detalhes", hasSize(4)))
                .andExpect(jsonPath("$.detalhes", hasItem("CONTATADO_BEM: Contatado e bem")));
        assertThat(contar("tmc_contato")).isZero();
    }

    @Test
    void agenteDeOutraUbsRecebe403() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));

        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteB))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, "CONTATADO_BEM")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("A pessoa não pertence à sua UBS"));
    }

    @Test
    void cidadaoEGestorNaoRegistramContato() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
        for (var usuario : new br.com.fiap.ThermoCity.user.model.User[]{cidadao, gestor}) {
            mockMvc.perform(post("/contatos").header("Authorization", bearer(usuario))
                            .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, "CONTATADO_BEM")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void pessoaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(contatoJson(999999, "CONTATADO_BEM")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pessoa não encontrada"));
    }

    @Test
    void semPessoaEResultadoResponde400ComOsDoisCampos() throws Exception {
        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes", hasSize(2)));
    }

    // ---------- GET /contatos ----------

    @Test
    void historicoDaPessoaEPaginadoDoMaisRecente() throws Exception {
        long pessoa = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
        for (String resultado : new String[]{"NAO_ATENDEU", "NAO_ATENDEU", "CONTATADO_BEM"}) {
            mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                            .contentType(MediaType.APPLICATION_JSON).content(contatoJson(pessoa, resultado)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/contatos?pessoa=" + pessoa + "&page=0&size=2").header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(2)))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo[0].resultado").value("CONTATADO_BEM"));

        mockMvc.perform(get("/contatos?pessoa=" + pessoa).header("Authorization", bearer(agenteB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/contatos").header("Authorization", bearer(agenteB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(get("/contatos").header("Authorization", bearer(cidadao)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/contatos?resultado=XYZ").header("Authorization", bearer(gestor)))
                .andExpect(status().isUnprocessableContent());
    }

    // ---------- GET /cobertura/hoje ----------

    @Test
    void coberturaDoDiaPorUbsESetor() throws Exception {
        long listaA = criarLista(hoje(), UBS_A);
        long listaB = criarLista(hoje(), UBS_B);
        // UBS A: 8 pessoas, 5 contatadas (3 bem + 2 precisam de ajuda) = 62,5%
        String[] statusA = {"CONTATADO_BEM", "CONTATADO_BEM", "CONTATADO_BEM", "PRECISA_AJUDA", "PRECISA_AJUDA",
                "NAO_ATENDEU", "PENDENTE", "DADOS_INCORRETOS"};
        for (int i = 0; i < statusA.length; i++) {
            long p = cadastrarPessoa(cidadao, pessoaJson("Pessoa A" + i));
            incluirNaLista(listaA, p, i + 1, SETOR_A, statusA[i]);
        }
        // UBS B: 2 pessoas, nenhuma contatada
        for (int i = 0; i < 2; i++) {
            long p = cadastrarPessoa(cidadao,
                    pessoaJson("Pessoa B" + i, hoje().minusYears(66).toString(), -22.12, -51.37, "true"));
            incluirNaLista(listaB, p, i + 1, SETOR_B, "PENDENTE");
        }

        mockMvc.perform(get("/cobertura/hoje").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(hoje().toString()))
                .andExpect(jsonPath("$.nivelCalor").value(4))
                .andExpect(jsonPath("$.resumo.total").value(10))
                .andExpect(jsonPath("$.resumo.contatados").value(5))
                .andExpect(jsonPath("$.resumo.coberturaPercentual").value(50.0))
                // menor cobertura primeiro
                .andExpect(jsonPath("$.ubs[0].ubsId").value(UBS_B))
                .andExpect(jsonPath("$.ubs[1].ubsId").value(UBS_A))
                .andExpect(jsonPath("$.ubs[1].resumo.total").value(8))
                .andExpect(jsonPath("$.ubs[1].resumo.contatados").value(5))
                .andExpect(jsonPath("$.ubs[1].resumo.pendentes").value(2))
                .andExpect(jsonPath("$.ubs[1].resumo.precisaAjuda").value(2))
                .andExpect(jsonPath("$.ubs[1].resumo.naoAtendeu").value(1))
                .andExpect(jsonPath("$.ubs[1].resumo.dadosIncorretos").value(1))
                .andExpect(jsonPath("$.ubs[1].resumo.coberturaPercentual").value(62.5))
                .andExpect(jsonPath("$.ubs[1].setores[0].setorCodigo").value(SETOR_A))
                .andExpect(jsonPath("$.ubs[1].setores[0].bairro").value("CENTRO"))
                .andExpect(jsonPath("$.ubs[1].setores[0].resumo.coberturaPercentual").value(62.5));
    }

    @Test
    void coberturaSemListaNoDiaResponde204() throws Exception {
        mockMvc.perform(get("/cobertura/hoje").header("Authorization", bearer(gestor)))
                .andExpect(status().isNoContent());
    }

    @Test
    void agenteNaoVeCobertura() throws Exception {
        mockMvc.perform(get("/cobertura/hoje").header("Authorization", bearer(agenteA)))
                .andExpect(status().isForbidden());
    }
}
