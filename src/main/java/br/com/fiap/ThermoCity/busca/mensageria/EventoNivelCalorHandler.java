package br.com.fiap.ThermoCity.busca.mensageria;

import br.com.fiap.ThermoCity.busca.evento.EventoNivelCalor;
import br.com.fiap.ThermoCity.busca.service.GeracaoListaService;
import br.com.fiap.ThermoCity.busca.service.ResultadoProcessamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Trata o corpo JSON do evento de nível de calor recebido pela fila.
 * Mensagem inválida (JSON ruim, sem nível, sem data...) é rejeitada sem reenfileirar: vai para a fila de
 * erro, com o motivo no log. Fica separado do listener para ser testado sem um broker.
 */
@Component
public class EventoNivelCalorHandler {

    private static final Logger log = LoggerFactory.getLogger(EventoNivelCalorHandler.class);

    private final GeracaoListaService geracaoService;
    private final JsonMapper jsonMapper;

    public EventoNivelCalorHandler(GeracaoListaService geracaoService, JsonMapper jsonMapper) {
        this.geracaoService = geracaoService;
        this.jsonMapper = jsonMapper;
    }

    /** Processa o corpo JSON da mensagem; lança AmqpRejectAndDontRequeueException se for inválido. */
    public ResultadoProcessamento tratar(String corpo) {
        EventoNivelCalor evento;
        try {
            evento = jsonMapper.readValue(corpo, EventoNivelCalor.class);
        } catch (JacksonException e) {
            throw rejeitar("JSON inválido: " + e.getOriginalMessage(), corpo);
        }
        if (evento == null) {
            throw rejeitar("mensagem vazia", corpo);
        }
        List<String> erros = evento.erros();
        if (!erros.isEmpty()) {
            throw rejeitar(String.join("; ", erros), corpo);
        }
        return geracaoService.processar(evento);
    }

    private static AmqpRejectAndDontRequeueException rejeitar(String motivo, String corpo) {
        log.warn("Evento de nível de calor enviado para a fila de erro: {}. Mensagem: {}", motivo, resumir(corpo));
        return new AmqpRejectAndDontRequeueException("Evento inválido: " + motivo);
    }

    private static String resumir(String corpo) {
        return corpo == null ? "" : corpo.length() <= 500 ? corpo : corpo.substring(0, 500) + "...";
    }
}
