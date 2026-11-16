package br.com.fiap.ThermoCity.user;

import br.com.fiap.ThermoCity.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioControllerTest extends ApiTestBase {

    private static String usuarioJson(String papel, String ubsId) {
        return """
                {"nome":"Joana Agente","email":"joana@saude.gov.br","senha":"senhaForte123",
                 "telefone":"(18) 99999-1234","cpf":"935.411.347-80","cep":"19010-000",
                 "papel":"%s","ubsId":%s}""".formatted(papel, ubsId == null ? "null" : "\"" + ubsId + "\"");
    }

    @Test
    void gestorCriaAgenteVinculadoAUbs() throws Exception {
        mockMvc.perform(post("/usuarios").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content(usuarioJson("AGENTE", UBS_A)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("AGENTE"))
                .andExpect(jsonPath("$.ubsId").value(UBS_A))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void agenteSemUbsOuComUnidadeExcluidaResponde400() throws Exception {
        mockMvc.perform(post("/usuarios").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content(usuarioJson("AGENTE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A UBS é obrigatória para agentes"));
        mockMvc.perform(post("/usuarios").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content(usuarioJson("AGENTE", UBS_EXCLUIDA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("UBS inválida: " + UBS_EXCLUIDA));
    }

    @Test
    void papelCidadaoNaoECriadoPorAqui() throws Exception {
        mockMvc.perform(post("/usuarios").header("Authorization", bearer(gestor))
                        .contentType(MediaType.APPLICATION_JSON).content(usuarioJson("CIDADAO", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soGestorCriaUsuarios() throws Exception {
        mockMvc.perform(post("/usuarios").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content(usuarioJson("GESTOR", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroPublicoTambemRespondeEmAuthCadastro() throws Exception {
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome":"Ana","email":"ana@exemplo.com","senha":"senhaForte123",
                         "telefone":"(18) 99999-1234","cpf":"935.411.347-80","cep":"19010-000"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CIDADAO"));
    }
}
