package br.com.fiap.ThermoCity.exception;

/** 400: a requisição é bem formada, mas viola uma regra de negócio (ex.: menor de 60 anos). */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
