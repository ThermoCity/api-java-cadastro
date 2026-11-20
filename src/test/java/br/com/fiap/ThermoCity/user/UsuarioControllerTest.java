package br.com.fiap.ThermoCity.user;

import br.com.fiap.ThermoCity.geolocalizacao.Coordenadas;
import br.com.fiap.ThermoCity.support.ApiTestBase;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioControllerTest extends ApiTestBase {

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void cadastroPublicoEmAuthCadastroCriaCidadao() throws Exception {
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome":"Ana","email":"ana@exemplo.com","senha":"senhaForte123",
                         "telefone":"(18) 99999-1234","cpf":"935.411.347-80","cep":"19010-000"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CIDADAO"));
    }

    // ---------- GET /usuarios (GESTOR) ----------

    @Test
    void gestorListaTodosOsUsuariosOrdenadosPorNomeComCpfESemSenha() throws Exception {
        mockMvc.perform(get("/usuarios?size=100").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Usuário AGENTE"))
                .andExpect(jsonPath("$.conteudo[0].senha").doesNotExist())
                .andExpect(jsonPath("$.conteudo[0].cpf").value("39053344705"));
    }

    @Test
    void gestorFiltraPorPapelEPorUbs() throws Exception {
        mockMvc.perform(get("/usuarios?papel=agente").header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mockMvc.perform(get("/usuarios?papel=AGENTE&ubs=" + UBS_A).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].email").value("agente.a@exemplo.com"));
        mockMvc.perform(get("/usuarios?papel=CHEFE").header("Authorization", bearer(gestor)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soGestorListaEDetalhaUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(agenteA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios").header("Authorization", bearer(cidadao)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios/" + gestor.getIdUsuario()).header("Authorization", bearer(cidadao)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void gestorDetalhaUmUsuarioE404QuandoNaoExiste() throws Exception {
        mockMvc.perform(get("/usuarios/" + agenteA.getIdUsuario()).header("Authorization", bearer(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubsId").value(UBS_A));
        mockMvc.perform(get("/usuarios/999999").header("Authorization", bearer(gestor)))
                .andExpect(status().isNotFound());
    }

    // ---------- /usuarios/me (próprio cadastro) ----------

    private static String atualizacaoJson(String email, String cep) {
        return """
                {"nome":"Maria Souza","email":"%s","telefone":"(18) 98888-0000","cep":"%s"}"""
                .formatted(email, cep);
    }

    @Test
    void qualquerPapelVeOProprioCadastro() throws Exception {
        mockMvc.perform(get("/usuarios/me").header("Authorization", bearer(agenteA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("agente.a@exemplo.com"))
                .andExpect(jsonPath("$.papel").value("AGENTE"));
    }

    @Test
    void usuarioAlteraSomenteOProprioCadastro() throws Exception {
        when(cepGeocodingService.buscarCoordenadas("19015000"))
                .thenReturn(Optional.of(new Coordenadas(-22.125, -51.388)));

        mockMvc.perform(put("/usuarios/me").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson("Maria.Nova@Exemplo.com", "19015-000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cidadao.getIdUsuario()))
                .andExpect(jsonPath("$.nome").value("Maria Souza"))
                .andExpect(jsonPath("$.email").value("maria.nova@exemplo.com"))
                .andExpect(jsonPath("$.telefone").value("18988880000"))
                .andExpect(jsonPath("$.cep").value("19015000"))
                .andExpect(jsonPath("$.latitude").value(-22.125))
                .andExpect(jsonPath("$.papel").value("CIDADAO"));

        User atualizado = userRepository.findById(cidadao.getIdUsuario()).orElseThrow();
        assertThat(atualizado.getCpf()).isEqualTo("52998224725");
        User outro = userRepository.findById(outroCidadao.getIdUsuario()).orElseThrow();
        assertThat(outro.getNome()).isEqualTo("Usuário CIDADAO");
        assertThat(outro.getEmail()).isEqualTo("jose@exemplo.com");
    }

    @Test
    void naoTrocaPapelNemUbsPeloProprioCadastro() throws Exception {
        mockMvc.perform(put("/usuarios/me").header("Authorization", bearer(agenteA))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nome":"Agente A","email":"agente.a@exemplo.com","telefone":"(18) 98888-0000",
                                 "cep":"19010-000","papel":"GESTOR","ubsId":"CNES-B"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("AGENTE"))
                .andExpect(jsonPath("$.ubsId").value(UBS_A));
    }

    @Test
    void emailDeOutroUsuarioResponde409() throws Exception {
        mockMvc.perform(put("/usuarios/me").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson("jose@exemplo.com", "19010-000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"));
    }

    @Test
    void camposInvalidosNaAtualizacaoRespondem400() throws Exception {
        mockMvc.perform(put("/usuarios/me").header("Authorization", bearer(cidadao))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atualizacaoJson("nao-e-email", "123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trocaDeSenhaExigeASenhaAtual() throws Exception {
        User comSenha = userRepository.save(new User("Ana", "ana@exemplo.com",
                passwordEncoder.encode("senhaForte123"), "18999990000", "93541134780", "19010000",
                null, null, UserRole.CIDADAO));

        mockMvc.perform(put("/usuarios/me/senha").header("Authorization", bearer(comSenha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"errada123\",\"novaSenha\":\"novaSenha456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Senha atual incorreta"));

        mockMvc.perform(put("/usuarios/me/senha").header("Authorization", bearer(comSenha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senhaForte123\",\"novaSenha\":\"novaSenha456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@exemplo.com\",\"senha\":\"novaSenha456\"}"))
                .andExpect(status().isOk());
    }
}
