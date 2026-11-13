package br.com.fiap.ThermoCity.security.dto;

import java.time.Instant;

public record LoginResponseDTO(String token, String tipo, String papel, Instant expiraEm) {
}
