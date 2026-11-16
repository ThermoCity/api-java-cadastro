package br.com.fiap.ThermoCity.contato.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Resultados possíveis de um contato da busca ativa. */
public enum ResultadoContato {
    CONTATADO_BEM("Contatado e bem"),
    PRECISA_AJUDA("Contatado e precisa de ajuda (encaminhado à UBS ou ao refúgio)"),
    NAO_ATENDEU("Não atendeu (nova tentativa)"),
    DADOS_INCORRETOS("Endereço ou telefone incorreto");

    private final String descricao;

    ResultadoContato(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Aceita o código sem diferenciar maiúsculas/minúsculas, com espaços ou hífens no lugar de "_". */
    public static Optional<ResultadoContato> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return Arrays.stream(values()).filter(r -> r.name().equals(normalizado)).findFirst();
    }

    /** Lista "CODIGO: descrição" usada na resposta 422. */
    public static List<String> valoresValidos() {
        return Arrays.stream(values()).map(r -> r.name() + ": " + r.descricao).toList();
    }
}
