package br.com.fiap.ThermoCity.user.service;

import br.com.fiap.ThermoCity.exception.RegraNegocioException;
import br.com.fiap.ThermoCity.geolocalizacao.CepGeocodingService;
import br.com.fiap.ThermoCity.geolocalizacao.Coordenadas;
import br.com.fiap.ThermoCity.security.dto.RegisterDTO;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import br.com.fiap.ThermoCity.user.dto.NovoUsuarioDTO;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import br.com.fiap.ThermoCity.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CepGeocodingService cepGeocodingService;
    private final GeoService geoService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       CepGeocodingService cepGeocodingService, GeoService geoService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cepGeocodingService = cepGeocodingService;
        this.geoService = geoService;
    }

    /** Cadastro público: o papel é sempre CIDADAO, independentemente do que o cliente enviar. */
    @Transactional
    public User registrarCidadao(RegisterDTO dto) {
        return criar(dto.nome(), dto.email(), dto.senha(), dto.telefone(), dto.cpf(), dto.cep(),
                UserRole.CIDADAO, null);
    }

    /** Criação feita por um GESTOR: agentes (vinculados a uma UBS válida), gestores e o coletor. */
    @Transactional
    public User criarPorGestor(NovoUsuarioDTO dto) {
        UserRole papel = UserRole.valueOf(dto.papel());
        String ubsId = null;
        if (papel == UserRole.AGENTE) {
            ubsId = dto.ubsId() == null ? null : dto.ubsId().trim();
            if (ubsId == null || ubsId.isEmpty()) {
                throw new RegraNegocioException("A UBS é obrigatória para agentes");
            }
            if (!geoService.isUbsValida(ubsId)) {
                throw new RegraNegocioException("UBS inválida: " + ubsId);
            }
        }
        return criar(dto.nome(), dto.email(), dto.senha(), dto.telefone(), dto.cpf(), dto.cep(), papel, ubsId);
    }

    private User criar(String nome, String emailInformado, String senha, String telefone, String cpfInformado,
                       String cepInformado, UserRole papel, String ubsId) {
        String email = emailInformado.trim().toLowerCase(Locale.ROOT);
        String cpf = somenteDigitos(cpfInformado);
        String cep = somenteDigitos(cepInformado);

        if (userRepository.existsByEmail(email)) {
            throw new RecursoDuplicadoException("E-mail já cadastrado");
        }
        if (userRepository.existsByCpf(cpf)) {
            throw new RecursoDuplicadoException("CPF já cadastrado");
        }

        Optional<Coordenadas> coordenadas = cepGeocodingService.buscarCoordenadas(cep);

        User user = new User(
                nome.trim(),
                email,
                passwordEncoder.encode(senha),
                somenteDigitos(telefone),
                cpf,
                cep,
                coordenadas.map(Coordenadas::latitude).orElse(null),
                coordenadas.map(Coordenadas::longitude).orElse(null),
                papel,
                ubsId
        );
        return userRepository.save(user);
    }

    private static String somenteDigitos(String valor) {
        return valor.replaceAll("\\D", "");
    }
}
