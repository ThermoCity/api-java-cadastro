package br.com.fiap.ThermoCity.support;

import br.com.fiap.ThermoCity.config.ClockConfig;
import br.com.fiap.ThermoCity.geolocalizacao.CepGeocodingService;
import br.com.fiap.ThermoCity.security.infra.TokenService;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import br.com.fiap.ThermoCity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de API: território mínimo (2 setores, 2 UBS válidas e 1 unidade excluída) e
 * usuários de cada papel com token JWT.
 *
 * Setor A (IPVS 5) cobre lon -51.40..-51.38 e setor B (IPVS 3) cobre lon -51.38..-51.36, ambos com
 * lat -22.13..-22.11. A "prisão" fica exatamente no ponto padrão de cadastro, mas é excluída.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    protected static final String SETOR_A = "354140605000001";
    protected static final String SETOR_B = "354140605000002";
    protected static final String UBS_A = "CNES-A";
    protected static final String UBS_B = "CNES-B";
    protected static final String UBS_EXCLUIDA = "CNES-PRISAO";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TokenService tokenService;
    @Autowired
    protected GeoService geoService;

    @MockitoBean
    protected CepGeocodingService cepGeocodingService;

    protected User cidadao;
    protected User outroCidadao;
    protected User agenteA;
    protected User agenteB;
    protected User gestor;

    @BeforeEach
    void prepararBase() {
        limparBanco(jdbc);
        jdbc.update("insert into tmc_setor (codigo, nm_bairro, ipvs_grupo, lat_referencia, lon_referencia, "
                + "geometria_wkt) values (?, ?, ?, ?, ?, ?)", SETOR_A, "CENTRO", 5, -22.12, -51.39,
                "POLYGON ((-51.40 -22.13, -51.38 -22.13, -51.38 -22.11, -51.40 -22.11, -51.40 -22.13))");
        jdbc.update("insert into tmc_setor (codigo, nm_bairro, ipvs_grupo, lat_referencia, lon_referencia, "
                + "geometria_wkt) values (?, ?, ?, ?, ?, ?)", SETOR_B, "VILA MARCONDES", 3, -22.12, -51.37,
                "POLYGON ((-51.38 -22.13, -51.36 -22.13, -51.36 -22.11, -51.38 -22.11, -51.38 -22.13))");
        inserirServico(UBS_A, "UBS CENTRO", -22.12, -51.39);
        inserirServico(UBS_B, "UBS VILA MARCONDES", -22.12, -51.37);
        inserirServico(UBS_EXCLUIDA, "PENITENCIARIA", -22.12, -51.385);
        geoService.recarregar();

        when(cepGeocodingService.buscarCoordenadas(anyString())).thenReturn(Optional.empty());

        cidadao = salvarUsuario("maria@exemplo.com", "52998224725", UserRole.CIDADAO, null);
        outroCidadao = salvarUsuario("jose@exemplo.com", "11144477735", UserRole.CIDADAO, null);
        agenteA = salvarUsuario("agente.a@exemplo.com", "39053344705", UserRole.AGENTE, UBS_A);
        agenteB = salvarUsuario("agente.b@exemplo.com", "86288366757", UserRole.AGENTE, UBS_B);
        gestor = salvarUsuario("gestor@exemplo.com", "71428793860", UserRole.GESTOR, null);
    }

    /** Apaga tudo na ordem das chaves estrangeiras (o H2 é compartilhado entre as classes de teste). */
    public static void limparBanco(JdbcTemplate jdbc) {
        for (String tabela : new String[]{"tmc_contato", "tmc_lista_item", "tmc_lista_busca_ativa",
                "tmc_orientacao_contato", "tmc_pessoa_sintetica", "tmc_usuario", "tmc_servico", "tmc_setor"}) {
            jdbc.update("delete from " + tabela);
        }
    }

    protected String bearer(User usuario) {
        return "Bearer " + tokenService.generateToken(usuario).valor();
    }

    protected static LocalDate hoje() {
        return LocalDate.now(ClockConfig.FUSO);
    }

    /** Corpo de cadastro válido: 78 anos, mora sozinha, coordenada no setor A. */
    protected static String pessoaJson(String nome) {
        return pessoaJson(nome, hoje().minusYears(78).toString(), -22.12, -51.385, "true");
    }

    protected static String pessoaJson(String nome, String dataNascimento, double lat, double lon,
                                       String consentimento) {
        return """
                {"nome":"%s","dataNascimento":"%s","sexo":"F","telefone":"(18) 99999-1234",
                 "endereco":"Rua Siqueira Campos, 1200","cep":"19010-000","latitude":%s,"longitude":%s,
                 "moraSozinho":true,"mobilidadeReduzida":false,"condicaoCronica":true,
                 "contatoReferenciaNome":"Carlos (filho)","contatoReferenciaTelefone":"(18) 98888-7777",
                 "consentimentoLgpd":%s}""".formatted(nome, dataNascimento, lat, lon, consentimento);
    }

    /** Cadastra pelo endpoint (como o cidadão) e devolve o id. */
    protected long cadastrarPessoa(User titular, String json) throws Exception {
        String location = mockMvc.perform(post("/pessoas").header("Authorization", bearer(titular))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    protected Integer contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    private void inserirServico(String id, String nome, double lat, double lon) {
        jdbc.update("insert into tmc_servico (id, nome, tipo, lat, lon) values (?, ?, 'UBS', ?, ?)",
                id, nome, lat, lon);
    }

    private User salvarUsuario(String email, String cpf, UserRole papel, String ubsId) {
        return userRepository.save(new User("Usuário " + papel, email, "$2a$10$hashdeteste", "18999990000",
                cpf, "19010000", null, null, papel, ubsId));
    }
}
