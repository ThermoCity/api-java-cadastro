package br.com.fiap.ThermoCity.security;

import br.com.fiap.ThermoCity.geolocalizacao.CepGeocodingService;
import br.com.fiap.ThermoCity.geolocalizacao.CepNaoEncontradoException;
import br.com.fiap.ThermoCity.geolocalizacao.Coordenadas;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import br.com.fiap.ThermoCity.support.ApiTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationControllerTest {

    // CPF valido gerado para teste (nao pertence a ninguem)
    private static final String CADASTRO_VALIDO = """
            {"nome":"Maria Silva","email":"Maria@Exemplo.com","senha":"senhaForte123",
             "telefone":"(18) 99999-1234","cpf":"529.982.247-25","cep":"19010-000"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CepGeocodingService cepGeocodingService;

    @BeforeEach
    void limparBase() {
        ApiTestBase.limparBanco(jdbc);
    }

    @Test
    void cadastroValidoCriaCidadaoComCoordenadas() throws Exception {
        when(cepGeocodingService.buscarCoordenadas("19010000"))
                .thenReturn(Optional.of(new Coordenadas(-22.1228739, -51.3897085)));

        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CIDADAO"))
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"))
                .andExpect(jsonPath("$.cep").value("19010000"))
                .andExpect(jsonPath("$.latitude").value(-22.1228739))
                .andExpect(jsonPath("$.longitude").value(-51.3897085))
                .andExpect(jsonPath("$.localizacaoEncontrada").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.cpf").value("52998224725"));

        User salvo = userRepository.findByEmail("maria@exemplo.com").orElseThrow();
        assertThat(salvo.getCpf()).isEqualTo("52998224725");
        assertThat(salvo.getTelefone()).isEqualTo("18999991234");
        assertThat(salvo.getSenha()).isNotEqualTo("senhaForte123").startsWith("$2");
    }

    @Test
    void papelEnviadoPeloClienteEIgnorado() throws Exception {
        when(cepGeocodingService.buscarCoordenadas(anyString())).thenReturn(Optional.empty());
        String comPapel = CADASTRO_VALIDO.replace("\"nome\"", "\"papel\":\"GESTOR\",\"nome\"");

        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(comPapel))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CIDADAO"));
    }

    @Test
    void geocodificacaoIndisponivelCadastraSemCoordenadas() throws Exception {
        when(cepGeocodingService.buscarCoordenadas(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.localizacaoEncontrada").value(false));
    }

    @Test
    void cepInexistenteResponde400() throws Exception {
        when(cepGeocodingService.buscarCoordenadas(anyString())).thenThrow(new CepNaoEncontradoException("19010000"));

        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("CEP 19010000 não encontrado"));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void emailRepetidoResponde409() throws Exception {
        when(cepGeocodingService.buscarCoordenadas(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"));
    }

    @Test
    void camposInvalidosResponde400ComDetalhes() throws Exception {
        String invalido = """
                {"nome":"","email":"nao-e-email","senha":"123",
                 "telefone":"abc","cpf":"111.111.111-11","cep":"123"}""";

        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.length()").value(6));
    }

    @Test
    void loginValidoDevolveTokenComPapel() throws Exception {
        when(cepGeocodingService.buscarCoordenadas(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@exemplo.com\",\"senha\":\"senhaForte123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.papel").value("CIDADAO"))
                .andExpect(jsonPath("$.expiraEm").isNotEmpty());
    }

    @Test
    void senhaErradaResponde401() throws Exception {
        when(cepGeocodingService.buscarCoordenadas(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(CADASTRO_VALIDO))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@exemplo.com\",\"senha\":\"errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
    }

    @Test
    void endpointProtegidoSemTokenResponde401() throws Exception {
        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/pessoas").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }
}
