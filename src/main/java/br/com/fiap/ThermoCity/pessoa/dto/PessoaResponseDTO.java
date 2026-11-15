package br.com.fiap.ThermoCity.pessoa.dto;

import br.com.fiap.ThermoCity.pessoa.model.Pessoa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Pessoa acompanhada, com setor e UBS de referência calculados pela localização")
public record PessoaResponseDTO(
        @Schema(example = "20052") Long id,
        @Schema(example = "Dona Aparecida Souza") String nome,
        @Schema(example = "1948-03-15", nullable = true) LocalDate dataNascimento,
        @Schema(example = "78") Integer idade,
        @Schema(example = "70+") String faixaEtaria,
        @Schema(example = "F", nullable = true) String sexo,
        @Schema(example = "18999991234") String telefone,
        @Schema(example = "Rua Siqueira Campos, 1200 - Centro", nullable = true) String endereco,
        @Schema(example = "19010000", nullable = true) String cep,
        @Schema(example = "-22.1228739") Double latitude,
        @Schema(example = "-51.3897085") Double longitude,
        @Schema(example = "true") boolean moraSozinho,
        @Schema(example = "false") boolean mobilidadeReduzida,
        @Schema(example = "true") boolean condicaoCronica,
        @Schema(example = "Carlos Souza (filho)", nullable = true) String contatoReferenciaNome,
        @Schema(example = "18988887777", nullable = true) String contatoReferenciaTelefone,
        @Schema(example = "354140605000009") String setorCodigo,
        @Schema(example = "CENTRO", nullable = true) String setorBairro,
        @Schema(example = "5", nullable = true, description = "Grupo do IPVS (1 a 6)") Integer setorIpvsGrupo,
        @Schema(example = "CNES-2755114") String ubsId,
        @Schema(example = "UBS CENTRO AUX ENF GUSTAVO LOURENCO") String ubsNome,
        @Schema(example = "true") boolean consentimentoLgpd,
        @Schema(example = "2026-11-14T21:10:00-03:00") OffsetDateTime consentimentoEm,
        @Schema(description = "true para as pessoas fictícias geradas a partir do Censo", example = "false")
        boolean sintetico,
        @Schema(example = "7", nullable = true) Long titularId,
        @Schema(example = "2026-11-14T21:10:00-03:00", nullable = true) OffsetDateTime atualizadoEm
) {
    public static PessoaResponseDTO from(Pessoa p, LocalDate hoje) {
        return new PessoaResponseDTO(
                p.getId(), p.getNome(), p.getDataNascimento(), p.idadeEm(hoje), p.getFaixaEtaria(), p.getSexo(),
                p.getTelefone(), p.getEndereco(), p.getCep(), p.getLatitude(), p.getLongitude(),
                p.isMoraSozinho(), p.isMobilidadeReduzida(), p.isCondicaoCronica(),
                p.getContatoReferenciaNome(), p.getContatoReferenciaTelefone(),
                p.getSetor().getCodigo(), p.getSetor().getBairro(), p.getSetor().getIpvsGrupo(),
                p.getUbsId(), p.getUbsNome(), p.isConsentimentoLgpd(), p.getConsentimentoEm(), p.isSintetico(),
                p.getTitular() != null ? p.getTitular().getIdUsuario() : null, p.getAtualizadoEm());
    }
}
