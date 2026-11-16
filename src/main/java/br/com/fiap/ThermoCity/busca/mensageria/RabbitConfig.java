package br.com.fiap.ThermoCity.busca.mensageria;

import br.com.fiap.ThermoCity.busca.config.MensageriaProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia declarada pela API Java na primeira conexão (o RabbitAdmin cria o que não existir):
 *
 * <pre>
 * API .NET ──publica──▶ thermocity.clima (topic) ──nivel-calor.alterado──▶ thermocity.cadastro.nivel-calor
 *                                                                              │ erro após 3 tentativas
 *                                   thermocity.clima.dlx (direct) ◀───────────┘
 *                                              └──▶ thermocity.cadastro.nivel-calor.dlq
 * </pre>
 */
@Configuration
public class RabbitConfig {

    private final MensageriaProperties nomes;

    public RabbitConfig(MensageriaProperties nomes) {
        this.nomes = nomes;
    }

    @Bean
    TopicExchange exchangeClima() {
        return new TopicExchange(nomes.exchange(), true, false);
    }

    @Bean
    DirectExchange exchangeErro() {
        return new DirectExchange(nomes.exchangeErro(), true, false);
    }

    @Bean
    Queue filaNivelCalor() {
        return QueueBuilder.durable(nomes.fila())
                .deadLetterExchange(nomes.exchangeErro())
                .deadLetterRoutingKey(nomes.filaErro())
                .build();
    }

    @Bean
    Queue filaErroNivelCalor() {
        return QueueBuilder.durable(nomes.filaErro()).build();
    }

    @Bean
    Binding ligacaoNivelCalor() {
        return BindingBuilder.bind(filaNivelCalor()).to(exchangeClima()).with(nomes.routingKey());
    }

    @Bean
    Binding ligacaoErro() {
        return BindingBuilder.bind(filaErroNivelCalor()).to(exchangeErro()).with(nomes.filaErro());
    }
}
