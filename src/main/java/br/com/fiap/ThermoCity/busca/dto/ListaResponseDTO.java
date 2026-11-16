package br.com.fiap.ThermoCity.busca.dto;

import br.com.fiap.ThermoCity.common.PaginaResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Lista priorizada de busca ativa do dia (de uma UBS, ou de todas para o gestor)")
public record ListaResponseDTO(
        @Schema(example = "2026-11-16") LocalDate data,
        @Schema(description = "Maior nível de calor do dia, que define o alcance", example = "4") int nivelCalor,
        @Schema(example = "v1") String versaoRegra,
        @Schema(description = "UBS consultada; nula quando o gestor consulta todas", example = "CNES-2755114",
                nullable = true) String ubsId,
        @Schema(example = "UBS CENTRO AUX ENF GUSTAVO LOURENCO", nullable = true) String ubsNome,
        @Schema(description = "Quantidade de listas somadas (uma por UBS)", example = "1") int listas,
        @Schema(description = "Itens em ordem de prioridade (por UBS e posição)") PaginaResponse<ListaItemResponseDTO> itens
) {
}
