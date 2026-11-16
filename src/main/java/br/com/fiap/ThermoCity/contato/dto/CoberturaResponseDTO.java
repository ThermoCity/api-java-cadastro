package br.com.fiap.ThermoCity.contato.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Cobertura da busca ativa do dia, total e por UBS e setor")
public record CoberturaResponseDTO(
        @Schema(example = "2026-11-15") LocalDate data,
        @Schema(description = "Maior nível de calor entre as listas do dia", example = "4") Integer nivelCalor,
        Resumo resumo,
        @Schema(description = "UBS ordenadas da menor para a maior cobertura") List<CoberturaUbs> ubs
) {

    @Schema(description = """
            Contagens. contatados = CONTATADO_BEM + PRECISA_AJUDA; pendentes = ainda sem contato + NAO_ATENDEU; \
            dadosIncorretos fica à parte. coberturaPercentual = contatados / total.""")
    public record Resumo(
            @Schema(example = "40") long total,
            @Schema(example = "25") long contatados,
            @Schema(example = "13") long pendentes,
            @Schema(example = "3") long precisaAjuda,
            @Schema(example = "4") long naoAtendeu,
            @Schema(example = "2") long dadosIncorretos,
            @Schema(example = "62.5") BigDecimal coberturaPercentual
    ) {
    }

    public record CoberturaUbs(
            @Schema(example = "CNES-2755114") String ubsId,
            @Schema(example = "UBS CENTRO AUX ENF GUSTAVO LOURENCO") String ubsNome,
            Resumo resumo,
            List<CoberturaSetor> setores
    ) {
    }

    public record CoberturaSetor(
            @Schema(example = "354140605000009") String setorCodigo,
            @Schema(example = "CENTRO", nullable = true) String bairro,
            Resumo resumo
    ) {
    }
}
