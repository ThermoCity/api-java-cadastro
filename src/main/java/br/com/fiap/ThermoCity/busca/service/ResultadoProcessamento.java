package br.com.fiap.ThermoCity.busca.service;

import br.com.fiap.ThermoCity.busca.model.AcaoEvento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Resultado do processamento de um evento de nível de calor")
public record ResultadoProcessamento(
        @Schema(example = "SIM-3f2a9c1e") String eventoId,
        @Schema(example = "2026-11-16") LocalDate dataLocal,
        @Schema(example = "4") int nivelNovo,
        @Schema(example = "LISTAS_GERADAS") AcaoEvento acao,
        @Schema(description = "Listas criadas, ampliadas ou encerradas (uma por UBS)", example = "33") int listasAfetadas,
        @Schema(description = "Pessoas que entraram nas listas neste evento", example = "812") int pessoasIncluidas,
        @Schema(description = "Total de pessoas nas listas do dia após o evento", example = "812") int totalNasListas,
        @Schema(description = "Pessoas sem setor ou UBS válida, deixadas de fora para correção do cadastro",
                example = "0") int pessoasSemCadastroCompleto
) {

    static ResultadoProcessamento semListas(String eventoId, LocalDate data, int nivel, AcaoEvento acao, int listas) {
        return new ResultadoProcessamento(eventoId, data, nivel, acao, listas, 0, 0, 0);
    }
}
