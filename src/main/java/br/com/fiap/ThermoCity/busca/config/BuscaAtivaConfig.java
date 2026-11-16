package br.com.fiap.ThermoCity.busca.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PrioridadeProperties.class, MensageriaProperties.class})
public class BuscaAtivaConfig {
}
