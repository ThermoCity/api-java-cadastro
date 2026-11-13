package br.com.fiap.ThermoCity.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErroResponse(int status, String erro, String mensagem, List<String> detalhes, OffsetDateTime timestamp) {

    public static ErroResponse of(int status, String erro, String mensagem, List<String> detalhes) {
        return new ErroResponse(status, erro, mensagem, detalhes, OffsetDateTime.now());
    }
}
