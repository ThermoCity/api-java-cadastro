package br.com.fiap.ThermoCity.busca.dto;

import br.com.fiap.ThermoCity.busca.model.ListaItem;
import br.com.fiap.ThermoCity.busca.model.StatusContato;
import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import br.com.fiap.ThermoCity.territorio.service.RefugioProximo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Pessoa na lista do dia, na ordem em que o agente deve contatar")
public record ListaItemResponseDTO(
        @Schema(description = "Id do item; o POST /contatos atualiza o status dele", example = "120") Long id,
        @Schema(example = "1") int posicao,
        @Schema(example = "14") int pontuacao,
        @Schema(example = "mora sozinha · condição crônica · 78 anos · setor nota 5 · IPVS 6 · refúgio a 1,2 km")
        String motivos,
        @Schema(example = "PENDENTE") StatusContato statusContato,
        @Schema(example = "2026-11-16T09:40:00-03:00", nullable = true) OffsetDateTime ultimoContatoEm,
        @Schema(example = "CNES-2755114") String ubsId,
        @Schema(example = "UBS CENTRO AUX ENF GUSTAVO LOURENCO") String ubsNome,
        @Schema(example = "354140605000009") String setorCodigo,
        @Schema(example = "CENTRO", nullable = true) String setorBairro,
        @Schema(description = "Nula se a pessoa pediu a exclusão dos dados (LGPD)", nullable = true)
        PessoaDaLista pessoa,
        @Schema(description = "Refúgio climático mais próximo da pessoa", nullable = true)
        Refugio refugioMaisProximo
) {

    public static ListaItemResponseDTO from(ListaItem item, LocalDate data, RefugioProximo refugio) {
        Pessoa p = item.getPessoa();
        return new ListaItemResponseDTO(
                item.getId(), item.getPosicao(), item.getPontuacao(), item.getMotivos(), item.getStatusContato(),
                item.getUltimoContatoEm(), item.getLista().getUbs().getId(), item.getLista().getUbs().getNome(),
                item.getSetor().getCodigo(), item.getSetor().getBairro(),
                p == null ? null : new PessoaDaLista(p.getId(), p.getNome(), p.idadeEm(data), p.getSexo(),
                        p.getTelefone(), p.getEndereco(), p.getLatitude(), p.getLongitude(), p.isMoraSozinho(),
                        p.isMobilidadeReduzida(), p.isCondicaoCronica(), p.getContatoReferenciaNome(),
                        p.getContatoReferenciaTelefone(), p.isSintetico()),
                refugio == null ? null : new Refugio(refugio.refugio().getId(), refugio.refugio().getNome(),
                        refugio.refugio().getEndereco(), Math.round(refugio.distanciaMetros())));
    }

    @Schema(description = "Dados para o agente contatar a pessoa")
    public record PessoaDaLista(
            @Schema(example = "20052") Long id,
            @Schema(example = "Dona Aparecida Souza") String nome,
            @Schema(example = "78", nullable = true) Integer idade,
            @Schema(example = "F", nullable = true) String sexo,
            @Schema(example = "18999991234") String telefone,
            @Schema(example = "Rua Siqueira Campos, 1200 - Centro", nullable = true) String endereco,
            @Schema(example = "-22.1228739") Double latitude,
            @Schema(example = "-51.3897085") Double longitude,
            @Schema(example = "true") boolean moraSozinho,
            @Schema(example = "false") boolean mobilidadeReduzida,
            @Schema(example = "true") boolean condicaoCronica,
            @Schema(example = "Carlos Souza (filho)", nullable = true) String contatoReferenciaNome,
            @Schema(example = "18988887777", nullable = true) String contatoReferenciaTelefone,
            @Schema(description = "true para as pessoas fictícias geradas a partir do Censo") boolean sintetico
    ) {
    }

    @Schema(description = "Refúgio climático indicado para a pessoa")
    public record Refugio(
            @Schema(example = "OSM-node-123456") String id,
            @Schema(example = "Biblioteca Municipal") String nome,
            @Schema(nullable = true) String endereco,
            @Schema(description = "Distância em linha reta, em metros", example = "1200") long distanciaMetros
    ) {
    }
}
