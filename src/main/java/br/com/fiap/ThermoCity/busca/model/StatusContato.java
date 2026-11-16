package br.com.fiap.ThermoCity.busca.model;

import br.com.fiap.ThermoCity.contato.model.ResultadoContato;

/** Situação de uma pessoa na lista do dia: PENDENTE até o primeiro contato, depois o último resultado. */
public enum StatusContato {
    PENDENTE,
    CONTATADO_BEM,
    PRECISA_AJUDA,
    NAO_ATENDEU,
    DADOS_INCORRETOS;

    public static StatusContato de(ResultadoContato resultado) {
        return valueOf(resultado.name());
    }

    /** Conta como "contatada" na cobertura: alguém falou com a pessoa. */
    public boolean isContatado() {
        return this == CONTATADO_BEM || this == PRECISA_AJUDA;
    }
}
