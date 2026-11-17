package br.com.fiap.ThermoCity.user.controller;

import br.com.fiap.ThermoCity.common.PaginaResponse;
import br.com.fiap.ThermoCity.exception.ErroResponse;
import br.com.fiap.ThermoCity.security.dto.UsuarioResponseDTO;
import br.com.fiap.ThermoCity.user.dto.AlterarSenhaDTO;
import br.com.fiap.ThermoCity.user.dto.AtualizarUsuarioDTO;
import br.com.fiap.ThermoCity.user.dto.NovoUsuarioDTO;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Cadastro de usuários pelo gestor e manutenção do próprio cadastro")
public class UsuarioController {

    private final UserService userService;

    public UsuarioController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Cria um agente, gestor ou coletor (GESTOR)",
            description = "Agentes precisam de uma UBS válida (`ubsId`), que define a lista e as pessoas que veem.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos, papel CIDADAO ou UBS inválida",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de GESTOR",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail ou CPF já cadastrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<UsuarioResponseDTO> criar(@RequestBody @Valid NovoUsuarioDTO dto) {
        User user = userService.criarPorGestor(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponseDTO.from(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Lista todos os usuários cadastrados, paginado e ordenado por nome (GESTOR)",
            description = "Filtros opcionais por papel e por UBS de atuação. Senha e CPF nunca são devolvidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuários"),
            @ApiResponse(responseCode = "400", description = "Papel inválido, page negativo ou size fora de 1 a 100",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de GESTOR",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<UsuarioResponseDTO> listar(
            @Parameter(description = "CIDADAO, AGENTE, GESTOR ou COLETOR") @RequestParam(required = false)
            String papel,
            @Parameter(description = "Id da UBS de atuação (ex.: CNES-2755114)") @RequestParam(required = false)
            String ubs,
            @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página (1 a 100)") @RequestParam(defaultValue = "10") int size) {
        return PaginaResponse.de(userService.listar(papel, ubs, page, size), UsuarioResponseDTO::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Detalha um usuário (GESTOR)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de GESTOR",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário não existe",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponseDTO buscar(@PathVariable Long id) {
        return UsuarioResponseDTO.from(userService.buscarPorId(id));
    }

    @GetMapping("/me")
    @Operation(summary = "Mostra o cadastro do usuário logado (qualquer papel)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cadastro do usuário do token"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponseDTO meuCadastro(@AuthenticationPrincipal User usuario) {
        return UsuarioResponseDTO.from(userService.buscarPorId(usuario.getIdUsuario()));
    }

    @PutMapping("/me")
    @Operation(summary = "Altera o próprio cadastro (qualquer papel)",
            description = """
                    Altera nome, e-mail, telefone e CEP **somente do usuário do token**; não há como alterar \
                    outro cadastro por aqui. CPF, papel e UBS não mudam. Se o CEP mudar, as coordenadas são \
                    recalculadas. Se o e-mail mudar, o token atual deixa de valer: faça login de novo.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cadastro atualizado"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "E-mail já usado por outro usuário",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public UsuarioResponseDTO atualizarMeuCadastro(@AuthenticationPrincipal User usuario,
                                                   @RequestBody @Valid AtualizarUsuarioDTO dto) {
        return UsuarioResponseDTO.from(userService.atualizarProprio(usuario, dto));
    }

    @PutMapping("/me/senha")
    @Operation(summary = "Troca a própria senha (qualquer papel)",
            description = "Exige a senha atual. O token atual continua válido até expirar.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha alterada"),
            @ApiResponse(responseCode = "400", description = "Senha atual incorreta ou nova senha inválida",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<Void> alterarMinhaSenha(@AuthenticationPrincipal User usuario,
                                                  @RequestBody @Valid AlterarSenhaDTO dto) {
        userService.alterarSenha(usuario, dto);
        return ResponseEntity.noContent().build();
    }
}
