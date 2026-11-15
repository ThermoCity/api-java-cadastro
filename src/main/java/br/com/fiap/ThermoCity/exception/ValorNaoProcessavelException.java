package br.com.fiap.ThermoCity.exception;

import java.util.List;

/** 422: um valor de domínio não está entre os permitidos; detalhes lista os valores válidos. */
public class ValorNaoProcessavelException extends RuntimeException {

    private final List<String> detalhes;

    public ValorNaoProcessavelException(String mensagem, List<String> detalhes) {
        super(mensagem);
        this.detalhes = List.copyOf(detalhes);
    }

    public List<String> getDetalhes() {
        return detalhes;
    }
}
