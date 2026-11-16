package br.com.fiap.ThermoCity.busca.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Nível de calor simulado pelo gestor (demonstração e testes)")
public record SimulacaoNivelRequestDTO(

        @Schema(example = "4", description = "1 a 5. A partir de 3 gera as listas; abaixo de 3 encerra as do dia")
        @NotNull(message = "O nível é obrigatório")
        @Min(value = 1, message = "O nível deve estar entre 1 e 5")
        @Max(value = 5, message = "O nível deve estar entre 1 e 5")
        Integer nivelNovo,

        @Schema(example = "2026-11-16", nullable = true, description = "Dia do nível; padrão: hoje")
        LocalDate dataLocal
) {
}
