package br.com.fiap.ThermoCity.busca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Nomes da exchange, das filas e da routing key do evento de nível de calor (thermocity.rabbitmq.*). */
@ConfigurationProperties("thermocity.rabbitmq")
public record MensageriaProperties(
        @DefaultValue("thermocity.clima") String exchange,
        @DefaultValue("nivel-calor.alterado") String routingKey,
        @DefaultValue("thermocity.cadastro.nivel-calor") String fila,
        @DefaultValue("thermocity.clima.dlx") String exchangeErro,
        @DefaultValue("thermocity.cadastro.nivel-calor.dlq") String filaErro
) {
}
