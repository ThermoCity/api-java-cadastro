package br.com.fiap.ThermoCity.busca.evento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evento publicado pela API .NET quando o nível de calor de um dia (hoje ou previsão) muda.
 * Contrato: exchange thermocity.clima (topic), routing key nivel-calor.alterado, corpo JSON.
 *
 * <pre>
 * {"idEvento":"6f1c...","tipo":"NIVEL_CALOR_ALTERADO","dataLocal":"2026-11-16",
 *  "nivelAnterior":2,"nivelNovo":3,"icMaxC":38.4,"origem":"PREVISTO",
 *  "ocorridoEm":"2026-11-15T06:00:00-03:00"}
 * </pre>
 * Obrigatórios: idEvento, dataLocal e nivelNovo (1 a 5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoNivelCalor(
        String idEvento,
        String tipo,
        LocalDate dataLocal,
        Integer nivelAnterior,
        Integer nivelNovo,
        Double icMaxC,
        String origem,
        OffsetDateTime ocorridoEm
) {

    public static final String TIPO = "NIVEL_CALOR_ALTERADO";

    /** Motivos que impedem o processamento; vazio quando o evento é válido. */
    public List<String> erros() {
        List<String> erros = new ArrayList<>();
        if (idEvento == null || idEvento.isBlank()) {
            erros.add("idEvento ausente");
        } else if (idEvento.length() > 64) {
            erros.add("idEvento com mais de 64 caracteres");
        }
        if (dataLocal == null) {
            erros.add("dataLocal ausente");
        }
        if (nivelNovo == null) {
            erros.add("nivelNovo ausente");
        } else if (nivelNovo < 1 || nivelNovo > 5) {
            erros.add("nivelNovo fora de 1 a 5: " + nivelNovo);
        }
        if (tipo != null && !TIPO.equals(tipo)) {
            erros.add("tipo desconhecido: " + tipo);
        }
        return erros;
    }
}
