package br.com.fiap.ThermoCity.user.service;

import br.com.fiap.ThermoCity.geolocalizacao.CepGeocodingService;
import br.com.fiap.ThermoCity.geolocalizacao.Coordenadas;
import br.com.fiap.ThermoCity.security.dto.RegisterDTO;
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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       CepGeocodingService cepGeocodingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cepGeocodingService = cepGeocodingService;
    }

    @Transactional
    public User registrarCidadao(RegisterDTO dto) {
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        String cpf = somenteDigitos(dto.cpf());
        String cep = somenteDigitos(dto.cep());

        if (userRepository.existsByEmail(email)) {
            throw new RecursoDuplicadoException("E-mail já cadastrado");
        }
        if (userRepository.existsByCpf(cpf)) {
            throw new RecursoDuplicadoException("CPF já cadastrado");
        }

        Optional<Coordenadas> coordenadas = cepGeocodingService.buscarCoordenadas(cep);

        User user = new User(
                dto.nome().trim(),
                email,
                passwordEncoder.encode(dto.senha()),
                somenteDigitos(dto.telefone()),
                cpf,
                cep,
                coordenadas.map(Coordenadas::latitude).orElse(null),
                coordenadas.map(Coordenadas::longitude).orElse(null),
                UserRole.CIDADAO
        );
        return userRepository.save(user);
    }

    private static String somenteDigitos(String valor) {
        return valor.replaceAll("\\D", "");
    }
}
