package br.com.fiap.ThermoCity.busca.mensageria;

import br.com.fiap.ThermoCity.busca.config.MensageriaProperties;
import br.com.fiap.ThermoCity.busca.evento.EventoNivelCalor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/** Publica um evento de nível de calor no mesmo formato da API .NET (usado pela simulação do gestor). */
@Component
public class NivelCalorPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final MensageriaProperties nomes;

    public NivelCalorPublisher(RabbitTemplate rabbitTemplate, JsonMapper jsonMapper, MensageriaProperties nomes) {
        this.rabbitTemplate = rabbitTemplate;
        this.jsonMapper = jsonMapper;
        this.nomes = nomes;
    }

    public void publicar(EventoNivelCalor evento) {
        Message mensagem = MessageBuilder
                .withBody(jsonMapper.writeValueAsString(evento).getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(evento.idEvento())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbitTemplate.send(nomes.exchange(), nomes.routingKey(), mensagem);
    }
}
