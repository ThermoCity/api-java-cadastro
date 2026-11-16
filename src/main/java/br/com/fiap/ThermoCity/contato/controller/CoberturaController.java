package br.com.fiap.ThermoCity.contato.controller;

import br.com.fiap.ThermoCity.contato.dto.CoberturaResponseDTO;
import br.com.fiap.ThermoCity.contato.service.CoberturaService;
import br.com.fiap.ThermoCity.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cobertura")
@Tag(name = "Cobertura da busca ativa", description = "Acompanhamento do gestor durante a onda de calor")
public class CoberturaController {

    private final CoberturaService coberturaService;

    public CoberturaController(CoberturaService coberturaService) {
        this.coberturaService = coberturaService;
    }

    @GetMapping("/hoje")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Cobertura do dia por UBS e setor (GESTOR)",
            description = """
                    Total na lista, contatados, pendentes e "precisa de ajuda". Ex.: 40 na lista e 25 contatados \
                    = 62,5% de cobertura. Responde 204 quando não há lista no dia (NC1 ou NC2).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cobertura do dia"),
            @ApiResponse(responseCode = "204", description = "Sem busca ativa hoje", content = @Content),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Papel diferente de GESTOR",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<CoberturaResponseDTO> hoje() {
        return coberturaService.doDia()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
