package br.com.fiap.ThermoCity.contato.dto;

import br.com.fiap.ThermoCity.busca.model.ListaItem;
import br.com.fiap.ThermoCity.contato.model.Contato;
import br.com.fiap.ThermoCity.contato.model.ResultadoContato;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Contato registrado. pessoaId e pessoaNome ficam nulos se a pessoa foi excluída (LGPD).")
public record ContatoResponseDTO(
        @Schema(example = "1") Long id,
        @Schema(example = "15002", nullable = true) Long pessoaId,
        @Schema(example = "Dona Aparecida Souza", nullable = true) String pessoaNome,
        @Schema(example = "PRECISA_AJUDA") ResultadoContato resultado,
        @Schema(example = "Contatado e precisa de ajuda (encaminhado à UBS ou ao refúgio)") String resultadoDescricao,
        @Schema(description = "true quando o resultado é PRECISA_AJUDA: a pessoa aparece destacada para o gestor",
                example = "true") boolean destaque,
        @Schema(example = "Tontura e sem água gelada em casa. Encaminhada ao refúgio.", nullable = true)
        String observacao,
        @Schema(example = "3") Long agenteId,
        @Schema(example = "Joana Agente") String agenteNome,
        @Schema(example = "CNES-2755114") String ubsId,
        @Schema(example = "UBS CENTRO AUX ENF GUSTAVO LOURENCO") String ubsNome,
        @Schema(example = "2026-11-15T09:40:00-03:00") OffsetDateTime realizadoEm,
        @Schema(description = "Item da lista do dia atualizado; nulo se a pessoa não estava numa lista aberta",
                example = "120", nullable = true) Long listaItemId
) {
    public static ContatoResponseDTO from(Contato c) {
        ListaItem item = c.getListaItem();
        return new ContatoResponseDTO(
                c.getId(),
                c.getPessoa() != null ? c.getPessoa().getId() : null,
                c.getPessoa() != null ? c.getPessoa().getNome() : null,
                c.getResultado(),
                c.getResultado().getDescricao(),
                c.getResultado() == ResultadoContato.PRECISA_AJUDA,
                c.getObservacao(),
                c.getAgente().getIdUsuario(),
                c.getAgente().getNome(),
                c.getUbs().getId(),
                c.getUbs().getNome(),
                c.getRealizadoEm(),
                item != null ? item.getId() : null);
    }
}
