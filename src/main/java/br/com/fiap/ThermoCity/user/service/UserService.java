package br.com.fiap.ThermoCity.user.service;

import br.com.fiap.ThermoCity.common.Paginacao;
import br.com.fiap.ThermoCity.exception.RecursoNaoEncontradoException;
import br.com.fiap.ThermoCity.exception.RegraNegocioException;
import br.com.fiap.ThermoCity.geolocalizacao.CepGeocodingService;
import br.com.fiap.ThermoCity.geolocalizacao.Coordenadas;
import br.com.fiap.ThermoCity.security.dto.RegisterDTO;
import br.com.fiap.ThermoCity.territorio.service.GeoService;
import br.com.fiap.ThermoCity.user.dto.AlterarSenhaDTO;
import br.com.fiap.ThermoCity.user.dto.AtualizarUsuarioDTO;
import br.com.fiap.ThermoCity.user.dto.NovoUsuarioDTO;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.model.UserRole;
import br.com.fiap.ThermoCity.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {

    private static final Sort ORDEM_NOME = Sort.by(Sort.Order.asc("nome"), Sort.Order.asc("idUsuario"));

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

    /** Listagem do GESTOR, paginada e ordenada por nome, com filtros opcionais por papel e UBS. */
    public Page<User> listar(String papel, String ubsId, int pagina, int tamanho) {
        UserRole papelFiltro = null;
        if (papel != null && !papel.isBlank()) {
            try {
                papelFiltro = UserRole.valueOf(papel.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new RegraNegocioException("Papel inválido. Use CIDADAO, AGENTE, GESTOR ou COLETOR");
            }
        }
        String ubsFiltro = ubsId == null || ubsId.isBlank() ? null : ubsId.trim();
        return userRepository.buscar(papelFiltro, ubsFiltro, Paginacao.de(pagina, tamanho, ORDEM_NOME));
    }

    public User buscarPorId(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + id));
    }

    /**
     * O próprio usuário altera nome, e-mail, telefone e CEP. Sem @Transactional de propósito: a consulta
     * de CEP é uma chamada HTTP externa e não deve segurar uma conexão do banco; o save já é transacional.
     */
    public User atualizarProprio(User autenticado, AtualizarUsuarioDTO dto) {
        User user = buscarPorId(autenticado.getIdUsuario());
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        String cep = somenteDigitos(dto.cep());

        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new RecursoDuplicadoException("E-mail já cadastrado");
        }

        Double latitude = user.getLatitude();
        Double longitude = user.getLongitude();
        if (!cep.equals(user.getCep())) {
            Optional<Coordenadas> coordenadas = cepGeocodingService.buscarCoordenadas(cep);
            latitude = coordenadas.map(Coordenadas::latitude).orElse(null);
            longitude = coordenadas.map(Coordenadas::longitude).orElse(null);
        }

        user.atualizarDados(dto.nome().trim(), email, somenteDigitos(dto.telefone()), cep, latitude, longitude);
        return userRepository.save(user);
    }

    /** Troca de senha do próprio usuário: exige a senha atual. */
    public void alterarSenha(User autenticado, AlterarSenhaDTO dto) {
        User user = buscarPorId(autenticado.getIdUsuario());
        if (!passwordEncoder.matches(dto.senhaAtual(), user.getSenha())) {
            throw new RegraNegocioException("Senha atual incorreta");
        }
        if (dto.senhaAtual().equals(dto.novaSenha())) {
            throw new RegraNegocioException("A nova senha deve ser diferente da atual");
        }
        user.alterarSenha(passwordEncoder.encode(dto.novaSenha()));
        userRepository.save(user);
    }

    private static String somenteDigitos(String valor) {
        return valor.replaceAll("\\D", "");
    }
}
