package br.com.fiap.ThermoCity.exception;

/** 404: o recurso pedido não existe. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
