package br.com.fiap.ThermoCity.territorio.service;

import br.com.fiap.ThermoCity.territorio.model.Servico;

/** Refúgio climático mais próximo de uma coordenada e a distância em linha reta até ele. */
public record RefugioProximo(Servico refugio, double distanciaMetros) {
}
