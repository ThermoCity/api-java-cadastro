package br.com.fiap.ThermoCity.busca.controller;

import br.com.fiap.ThermoCity.busca.dto.ListaResponseDTO;
import br.com.fiap.ThermoCity.busca.service.ListaConsultaService;
import br.com.fiap.ThermoCity.exception.ErroResponse;
import br.com.fiap.ThermoCity.user.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/listas")
@Tag(name = "Lista priorizada de busca ativa", description = """
        Gerada automaticamente quando o nível de calor do dia chega a NC3 ou mais (evento da API de clima). \
        Cada pessoa traz posição, pontuação e os motivos da prioridade.""")
public class ListaController {

    private final ListaConsultaService consultaService;

    public ListaController(ListaConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/hoje")
    @PreAuthorize("hasAnyRole('AGENTE', 'GESTOR')")
    @Operation(summary = "Lista do dia (AGENTE e GESTOR)",
            description = """
                    Ordenada por posição, com o status do contato de cada pessoa. O agente vê a lista da sua UBS \
                    (o parâmetro ubs é opcional para ele); o gestor informa a UBS ou recebe todas, por UBS e \
                    posição. Responde 204 quando não há busca ativa no dia (NC1 ou NC2).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista do dia"),
            @ApiResponse(responseCode = "204", description = "Sem busca ativa hoje", content = @Content),
            @ApiResponse(responseCode = "400", description = "Paginação inválida",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "CIDADAO, ou agente pedindo a lista de outra UBS",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<ListaResponseDTO> hoje(
            @AuthenticationPrincipal User usuario,
            @Parameter(description = "Id da UBS (ex.: CNES-2755114)") @RequestParam(required = false) String ubs,
            @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página (1 a 100)") @RequestParam(defaultValue = "20") int size) {
        return responder(consultaService.consultar(usuario, consultaService.hoje(), ubs, page, size)
                .orElse(null));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENTE', 'GESTOR')")
    @Operation(summary = "Lista de uma data (AGENTE e GESTOR)",
            description = """
                    Mesmo formato de /listas/hoje para outra data. A API de clima também classifica os próximos \
                    dias, então a lista de amanhã pode já estar pronta. Sem data, usa hoje.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista da data"),
            @ApiResponse(responseCode = "204", description = "Sem busca ativa na data", content = @Content),
            @ApiResponse(responseCode = "400", description = "Data ou paginação inválida",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "CIDADAO, ou agente pedindo a lista de outra UBS",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<ListaResponseDTO> daData(
            @AuthenticationPrincipal User usuario,
            @Parameter(description = "Data da lista (AAAA-MM-DD)", example = "2026-11-16")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @Parameter(description = "Id da UBS (ex.: CNES-2755114)") @RequestParam(required = false) String ubs,
            @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página (1 a 100)") @RequestParam(defaultValue = "20") int size) {
        LocalDate dia = data != null ? data : consultaService.hoje();
        return responder(consultaService.consultar(usuario, dia, ubs, page, size).orElse(null));
    }

    private static ResponseEntity<ListaResponseDTO> responder(ListaResponseDTO lista) {
        return lista != null ? ResponseEntity.ok(lista) : ResponseEntity.noContent().build();
    }
}
