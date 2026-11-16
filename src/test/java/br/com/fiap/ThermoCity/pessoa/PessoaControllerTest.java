package br.com.fiap.ThermoCity.pessoa;

import br.com.fiap.ThermoCity.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PessoaControllerTest extends ApiTestBase {

    // ---------- POST /pessoas ----------

    @Test
    void cadastroValidoDefineSetorUbsTitularEConsentimento() throws Exception {
        mockMvc.perform(post("/pessoas").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON).content(pessoaJson("Dona Aparecida")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/pessoas/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.idade").value(78))
                .andExpect(jsonPath("$.faixaEtaria").value("70+"))
                .andExpect(jsonPath("$.telefone").value("18999991234"))
                .andExpect(jsonPath("$.setorCodigo").value(SETOR_A))
                .andExpect(jsonPath("$.setorIpvsGrupo").value(5))
                // a "penitenciária" está no mesmo ponto, mas é excluída: fica a UBS válida mais próxima
                .andExpect(jsonPath("$.ubsId").value(UBS_A))
                .andExpect(jsonPath("$.ubsNome").value("UBS CENTRO"))
                .andExpect(jsonPath("$.titularId").value(cidadao.getIdUsuario()))
                .andExpect(jsonPath("$.consentimentoLgpd").value(true))
                .andExpect(jsonPath("$.consentimentoEm").isNotEmpty())
                .andExpect(jsonPath("$.sintetico").value(false));

        assertThat(contar("tmc_pessoa_sintetica")).isEqualTo(1);
    }

    @Test
    void semConsentimentoResponde400ENadaEGravado() throws Exception {
        for (String consentimento : new String[]{"false", "null"}) {
            mockMvc.perform(post("/pessoas").header("Authorization", bearer(cidadao))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pessoaJson("Sem Consentimento", hoje().minusYears(70).toString(),
                                    -22.12, -51.385, consentimento)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("É necessário o consentimento para o cadastro"));
        }
        assertThat(contar("tmc_pessoa_sintetica")).isZero();
    }

    @Test
    void menorDe60Responde400() throws Exception {
        String quase60 = hoje().minusYears(60).plusDays(1).toString();
        mockMvc.perform(post("/pessoas").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pessoaJson("Jovem Demais", quase60, -22.12, -51.385, "true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O acompanhamento é para pessoas com 60 anos ou mais"));
        assertThat(contar("tmc_pessoa_sintetica")).isZero();
    }

    @Test
    void coordenadaForaDePrudenteResponde400() throws Exception {
        mockMvc.perform(post("/pessoas").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pessoaJson("Mora em São Paulo", hoje().minusYears(70).toString(),
                                -23.55, -46.63, "true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Endereço fora da área atendida"));
        assertThat(contar("tmc_pessoa_sintetica")).isZero();
    }

    @Test
    void semTokenResponde401ENadaEGravado() throws Exception {
        mockMvc.perform(post("/pessoas").contentType(MediaType.APPLICATION_JSON).content(pessoaJson("Sem Token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        assertThat(contar("tmc_pessoa_sintetica")).isZero();
    }

    @Test
    void agenteNaoCadastraPessoa() throws Exception {
        mockMvc.perform(post("/pessoas").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(pessoaJson("Pelo Agente")))
                .andExpect(status().isForbidden());
    }

    @Test
    void doisCamposInvalidosListamOsDoisNosDetalhes() throws Exception {
        String invalido = pessoaJson("", hoje().minusYears(70).toString(), -22.12, -51.385, "true")
                .replace("(18) 99999-1234", "abc");
        mockMvc.perform(post("/pessoas").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos"))
                .andExpect(jsonPath("$.detalhes", hasSize(2)))
                .andExpect(jsonPath("$.detalhes", hasItem("nome: O nome é obrigatório")))
                .andExpect(jsonPath("$.detalhes",
                        hasItem("telefone: Telefone inválido. Use DDD + número, ex.: (18) 99999-1234")));
    }

    // ---------- GET /pessoas ----------

    @Test
    void listagemPaginadaDoCidadaoTraz10De12Em2Paginas() throws Exception {
        for (int i = 1; i <= 12; i++) {
            cadastrarPessoa(cidadao, pessoaJson("Pessoa %02d".formatted(13 - i)));
        }
        cadastrarPessoa(outroCidadao, pessoaJson("Pessoa de Outro Titular"));

        mockMvc.perform(get("/pessoas?page=0&size=10").header("Authorization", bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(10)))
                .andExpect(jsonPath("$.totalElementos").value(12))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Pessoa 01"))
                .andExpect(jsonPath("$.conteudo[9].nome").value("Pessoa 10"));

        mockMvc.perform(get("/pessoas?page=1&size=10").header("Authorization", bearer(cidadao)))
                .andExpect(jsonPath("$.conteudo", hasSize(2)))
                .andExpect(jsonPath("$.ultima").value(true));
    }

    @Test
    void agenteVeSoASuaUbsEGestorFiltraPorSetor() throws Exception {
        cadastrarPessoa(cidadao, pessoaJson("Do Setor A"));
        cadastrarPessoa(cidadao, pessoaJson("Do Setor B", hoje().minusYears(65).toString(), -22.12, -51.37, "true"));

        mockMvc.perform(get("/pessoas").header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Do Setor A"));

        mockMvc.perform(get("/pessoas?ubs=" + UBS_B).header("Authorization", bearer(agenteA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("O agente só consulta pessoas da sua UBS"));

        mockMvc.perform(get("/pessoas?setor=" + SETOR_B).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].ubsId").value(UBS_B));

        mockMvc.perform(get("/pessoas?nome=setor").header("Authorization", bearer(gestor)))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void paginacaoInvalidaResponde400() throws Exception {
        mockMvc.perform(get("/pessoas?size=500").header("Authorization", bearer(cidadao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O parâmetro size deve estar entre 1 e 100"));
    }

    // ---------- GET /pessoas/{id} ----------

    @Test
    void pessoaInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/pessoas/999999").header("Authorization", bearer(cidadao)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pessoa não encontrada"));
    }

    @Test
    void cidadaoNaoVeCadastroDeOutro() throws Exception {
        long id = cadastrarPessoa(cidadao, pessoaJson("Da Maria"));
        mockMvc.perform(get("/pessoas/" + id).header("Authorization", bearer(outroCidadao)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/pessoas/" + id).header("Authorization", bearer(cidadao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Da Maria"));
        mockMvc.perform(get("/pessoas/" + id).header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pessoas/" + id).header("Authorization", bearer(agenteB)))
                .andExpect(status().isForbidden());
    }

    // ---------- PUT /pessoas/{id} ----------

    @Test
    void titularAtualizaTelefoneEMudarCoordenadaRecalculaSetorEUbs() throws Exception {
        long id = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));

        mockMvc.perform(put("/pessoas/" + id).header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pessoaJson("Dona Aparecida").replace("(18) 99999-1234", "(18) 97777-0000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("18977770000"))
                .andExpect(jsonPath("$.setorCodigo").value(SETOR_A))
                .andExpect(jsonPath("$.atualizadoEm").isNotEmpty());

        mockMvc.perform(put("/pessoas/" + id).header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pessoaJson("Dona Aparecida", hoje().minusYears(78).toString(), -22.12, -51.37,
                                "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setorCodigo").value(SETOR_B))
                .andExpect(jsonPath("$.ubsId").value(UBS_B));
    }

    @Test
    void outroCidadaoNaoAlteraNemExclui() throws Exception {
        long id = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));

        mockMvc.perform(put("/pessoas/" + id).header("Authorization", bearer(outroCidadao))
                        .contentType(MediaType.APPLICATION_JSON).content(pessoaJson("Nome Trocado")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Somente o titular do cadastro pode alterá-lo ou excluí-lo"));
        mockMvc.perform(delete("/pessoas/" + id).header("Authorization", bearer(outroCidadao)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/pessoas/" + id).header("Authorization", bearer(gestor)))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select nome from tmc_pessoa_sintetica where id = ?", String.class, id))
                .isEqualTo("Dona Aparecida");
    }

    // ---------- DELETE /pessoas/{id} ----------

    @Test
    void exclusaoApagaPessoaEAnonimizaContatosEItensDeLista() throws Exception {
        long id = cadastrarPessoa(cidadao, pessoaJson("Dona Aparecida"));
        jdbc.update("insert into tmc_lista_busca_ativa (data_lista, ubs_id, nivel_calor, evento_id, versao_regra, "
                + "status, criada_em) values (?, ?, 4, 'evt-1', 'v1', 'ABERTA', current_timestamp)", hoje(), UBS_A);
        Long listaId = jdbc.queryForObject("select max(id) from tmc_lista_busca_ativa", Long.class);
        jdbc.update("insert into tmc_lista_item (lista_id, pessoa_id, setor_codigo, posicao, pontuacao, motivos, "
                + "status_contato) values (?, ?, ?, 1, 9, 'mora sozinha · 78 anos', 'PENDENTE')", listaId, id, SETOR_A);
        mockMvc.perform(post("/contatos").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pessoaId\":%d,\"resultado\":\"NAO_ATENDEU\",\"observacao\":\"Ligar de novo\"}"
                                .formatted(id)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/pessoas/" + id).header("Authorization", bearer(cidadao)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pessoas/" + id).header("Authorization", bearer(cidadao)))
                .andExpect(status().isNotFound());
        assertThat(contar("tmc_pessoa_sintetica")).isZero();
        assertThat(contar("tmc_contato")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tmc_contato where pessoa_id is null "
                + "and observacao is null and resultado = 'NAO_ATENDEU'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tmc_lista_item where pessoa_id is null",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void idNaoNumericoResponde400() throws Exception {
        mockMvc.perform(get("/pessoas/abc").header("Authorization", bearer(cidadao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", endsWith("id")));
    }
}
