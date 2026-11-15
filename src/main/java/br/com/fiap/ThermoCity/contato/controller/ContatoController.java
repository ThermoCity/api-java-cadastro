package br.com.fiap.ThermoCity.contato.controller;

import br.com.fiap.ThermoCity.common.PaginaResponse;
import br.com.fiap.ThermoCity.contato.dto.ContatoRequestDTO;
import br.com.fiap.ThermoCity.contato.dto.ContatoResponseDTO;
import br.com.fiap.ThermoCity.contato.model.Contato;
import br.com.fiap.ThermoCity.contato.service.ContatoService;
import br.com.fiap.ThermoCity.exception.ErroResponse;
import br.com.fiap.ThermoCity.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/contatos")
@Tag(name = "Contatos da busca ativa", description = """
        O agente registra o resultado de cada contato; a situação da pessoa na lista do dia é atualizada.""")
public class ContatoController {

    private static final String ERRO_422 = """
            {"status":422,"erro":"Unprocessable Content","mensagem":"Resultado de contato inválido",
             "detalhes":["CONTATADO_BEM: Contatado e bem",
                         "PRECISA_AJUDA: Contatado e precisa de ajuda (encaminhado à UBS ou ao refúgio)",
                         "NAO_ATENDEU: Não atendeu (nova tentativa)",
                         "DADOS_INCORRETOS: Endereço ou telefone incorreto"],
             "timestamp":"2026-11-15T09:40:00-03:00"}""";

    private final ContatoService contatoService;

    public ContatoController(ContatoService contatoService) {
        this.contatoService = contatoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENTE')")
    @Operation(summary = "Registra o resultado de um contato (AGENTE)",
            description = """
                    A pessoa precisa ser da UBS do agente. Se ela estiver na lista aberta do dia, a situação dela \
                    na lista passa a ser o resultado informado. PRECISA_AJUDA fica destacado para o gestor.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contato registrado; Location aponta para ele"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de AGENTE ou pessoa de outra UBS",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Resultado fora dos permitidos; detalhes lista os válidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class),
                            examples = @ExampleObject(value = ERRO_422)))
    })
    public ResponseEntity<ContatoResponseDTO> registrar(@AuthenticationPrincipal User agente,
                                                        @RequestBody @Valid ContatoRequestDTO dto) {
        Contato contato = contatoService.registrar(agente, dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(contato.getId()).toUri();
        return ResponseEntity.created(location).body(ContatoResponseDTO.from(contato));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENTE', 'GESTOR')")
    @Operation(summary = "Histórico de contatos, paginado, do mais recente para o mais antigo",
            description = """
                    **AGENTE**: contatos da sua UBS. **GESTOR**: todos. Use `resultado=PRECISA_AJUDA` para ver as \
                    pessoas destacadas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de contatos"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel sem acesso ou pessoa de outra UBS",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "422", description = "Filtro de resultado inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<ContatoResponseDTO> listar(
            @AuthenticationPrincipal User usuario,
            @Parameter(description = "Id da pessoa") @RequestParam(required = false) Long pessoa,
            @Parameter(description = "Filtra por resultado (ex.: PRECISA_AJUDA)") @RequestParam(required = false)
            String resultado,
            @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página (1 a 100)") @RequestParam(defaultValue = "10") int size) {
        return PaginaResponse.de(contatoService.listar(usuario, pessoa, resultado, page, size),
                ContatoResponseDTO::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENTE', 'GESTOR')")
    @Operation(summary = "Consulta um contato")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contato encontrado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Contato de outra UBS",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Contato não encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ContatoResponseDTO buscar(@AuthenticationPrincipal User usuario, @PathVariable Long id) {
        return ContatoResponseDTO.from(contatoService.buscar(usuario, id));
    }
}
