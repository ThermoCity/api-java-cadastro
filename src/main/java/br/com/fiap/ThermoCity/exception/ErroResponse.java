package br.com.fiap.ThermoCity.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Formato padrão de erro de todos os endpoints")
public record ErroResponse(
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String erro,
        @Schema(example = "Dados inválidos") String mensagem,
        @Schema(description = "Detalhes por campo, quando houver", example = "[\"cpf: CPF inválido\"]")
        List<String> detalhes,
        @Schema(example = "2026-11-12T23:40:00-03:00") OffsetDateTime timestamp
) {

    public static ErroResponse of(int status, String erro, String mensagem, List<String> detalhes) {
        return new ErroResponse(status, erro, mensagem, detalhes, OffsetDateTime.now());
    }
}
