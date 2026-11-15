package br.com.fiap.ThermoCity.user.controller;

import br.com.fiap.ThermoCity.exception.ErroResponse;
import br.com.fiap.ThermoCity.security.dto.UsuarioResponseDTO;
import br.com.fiap.ThermoCity.user.dto.NovoUsuarioDTO;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Criação de agentes, gestores e do coletor pelo gestor")
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
}
