package br.com.fiap.ThermoCity.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Token emitido no login")
public record LoginResponseDTO(
        @Schema(description = "JWT assinado com RS256", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token,
        @Schema(example = "Bearer")
        String tipo,
        @Schema(example = "CIDADAO", allowableValues = {"CIDADAO", "AGENTE", "GESTOR", "COLETOR"})
        String papel,
        @Schema(description = "Instante de expiração (UTC)", example = "2026-11-13T04:40:00Z")
        Instant expiraEm
) {
}
