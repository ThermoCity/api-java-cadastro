package br.com.fiap.ThermoCity.busca.repository;

import br.com.fiap.ThermoCity.busca.model.StatusContato;

/** Contagem de itens da lista do dia agrupada por UBS, setor e situação do contato. */
public record CoberturaLinha(String ubsId, String ubsNome, String setorCodigo, String bairro,
                             StatusContato status, long quantidade) {
}
