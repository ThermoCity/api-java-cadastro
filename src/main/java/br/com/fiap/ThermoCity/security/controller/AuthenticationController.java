package br.com.fiap.ThermoCity.security.controller;

import br.com.fiap.ThermoCity.exception.ErroResponse;
import br.com.fiap.ThermoCity.security.dto.AuthenticationDTO;
import br.com.fiap.ThermoCity.security.dto.LoginResponseDTO;
import br.com.fiap.ThermoCity.security.dto.RegisterDTO;
import br.com.fiap.ThermoCity.security.dto.UsuarioResponseDTO;
import br.com.fiap.ThermoCity.security.infra.TokenService;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Cadastro de cidadãos e emissão do token JWT usado por todas as aplicações")
@SecurityRequirements // endpoints públicos: não exigem token
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;

    public AuthenticationController(AuthenticationManager authenticationManager, TokenService tokenService,
                                    UserService userService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica um usuário e devolve o token JWT",
            description = "O token (RS256) carrega o e-mail no `sub` e os claims `papel` e `idUsuario`. "
                    + "Envie-o nas demais requisições no cabeçalho `Authorization: Bearer <token>`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos (não informa qual campo errou)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = """
                                    {"status":401,"erro":"Unauthorized","mensagem":"E-mail ou senha inválidos",
                                     "detalhes":[],"timestamp":"2026-11-12T23:40:00-03:00"}""")))
    })
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthenticationDTO dto) {
        var credenciais = new UsernamePasswordAuthenticationToken(
                dto.email().trim().toLowerCase(Locale.ROOT), dto.senha());
        var auth = authenticationManager.authenticate(credenciais);

        User user = (User) auth.getPrincipal();
        var token = tokenService.generateToken(user);
        return ResponseEntity.ok(new LoginResponseDTO(token.valor(), "Bearer", user.getPapel().name(), token.expiraEm()));
    }

    @PostMapping({"/cadastro", "/register"})
    @Operation(summary = "Cadastra um cidadão",
            description = "Cadastro público: o usuário é sempre criado com papel **CIDADAO** (outros papéis têm "
                    + "fluxo próprio). O CEP é convertido em latitude/longitude pela AwesomeAPI, com a BrasilAPI "
                    + "como reserva. Se os dois serviços estiverem fora do ar, o cadastro é concluído sem "
                    + "coordenadas (`localizacaoEncontrada: false`).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cidadão cadastrado"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos ou CEP inexistente",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = """
                                    {"status":400,"erro":"Bad Request","mensagem":"Dados inválidos",
                                     "detalhes":["cpf: CPF inválido","cep: CEP inválido. Use o formato 00000-000"],
                                     "timestamp":"2026-11-12T23:40:00-03:00"}"""))),
            @ApiResponse(responseCode = "409", description = "E-mail ou CPF já cadastrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = """
                                    {"status":409,"erro":"Conflict","mensagem":"E-mail já cadastrado",
                                     "detalhes":[],"timestamp":"2026-11-12T23:40:00-03:00"}""")))
    })
    public ResponseEntity<UsuarioResponseDTO> register(@RequestBody @Valid RegisterDTO dto) {
        User user = userService.registrarCidadao(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponseDTO.from(user));
    }
}
