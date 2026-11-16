package br.com.fiap.ThermoCity.busca.mensageria;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consome o evento de mudança do nível de calor publicado pela API .NET na fila
 * thermocity.cadastro.nivel-calor. Falha inesperada (ex.: banco fora) é tentada 3 vezes e depois vai para
 * a fila de erro. Desligado com thermocity.rabbitmq.consumidor-ativo=false (ex.: nos testes).
 */
@Component
@ConditionalOnProperty(name = "thermocity.rabbitmq.consumidor-ativo", havingValue = "true", matchIfMissing = true)
public class NivelCalorListener {

    private final EventoNivelCalorHandler handler;

    public NivelCalorListener(EventoNivelCalorHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = "${thermocity.rabbitmq.fila}")
    public void receber(Message mensagem) {
        handler.tratar(new String(mensagem.getBody(), StandardCharsets.UTF_8));
    }
}
