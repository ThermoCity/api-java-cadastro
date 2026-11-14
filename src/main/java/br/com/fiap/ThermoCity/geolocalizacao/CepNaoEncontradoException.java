package br.com.fiap.ThermoCity.geolocalizacao;

public class CepNaoEncontradoException extends RuntimeException {
    public CepNaoEncontradoException(String cep) {
        super("CEP " + cep + " não encontrado");
    }
}
