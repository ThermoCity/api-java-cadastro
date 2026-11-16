package br.com.fiap.ThermoCity.exception;

/** 403: o usuário está autenticado, mas não pode acessar este recurso específico. */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
