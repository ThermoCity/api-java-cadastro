package br.com.fiap.ThermoCity.user.service;

import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import br.com.fiap.ThermoCity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Cria o primeiro GESTOR na subida, a partir das variáveis GESTOR_INICIAL_EMAIL e GESTOR_INICIAL_SENHA.
 * Sem elas nada é feito. Assim nenhuma senha fica no código ou no script do banco; depois disso o gestor
 * cria os demais usuários por POST /usuarios.
 */
@Component
public class GestorInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GestorInicialRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String senha;

    public GestorInicialRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               @Value("${thermocity.gestor-inicial.email:}") String email,
                               @Value("${thermocity.gestor-inicial.senha:}") String senha) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || senha == null || senha.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }
        if (senha.length() < 8) {
            log.warn("GESTOR_INICIAL_SENHA tem menos de 8 caracteres; gestor inicial não foi criado");
            return;
        }
        userRepository.save(new User("Gestor ThermoCity", email, passwordEncoder.encode(senha),
                "00000000000", "00000000000", "00000000", null, null, UserRole.GESTOR));
        log.info("Gestor inicial criado: {}", email);
    }
}
