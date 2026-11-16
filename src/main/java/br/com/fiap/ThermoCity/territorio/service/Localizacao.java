package br.com.fiap.ThermoCity.territorio.service;

import br.com.fiap.ThermoCity.territorio.model.Servico;
import br.com.fiap.ThermoCity.territorio.model.Setor;

/** Resultado da geolocalização de uma coordenada: setor que a contém e UBS mais próxima. */
public record Localizacao(Setor setor, Servico ubs, double distanciaUbsMetros) {
}
