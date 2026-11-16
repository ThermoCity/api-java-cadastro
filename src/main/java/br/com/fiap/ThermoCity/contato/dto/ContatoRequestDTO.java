package br.com.fiap.ThermoCity.contato.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Resultado de um contato da busca ativa")
public record ContatoRequestDTO(

        @Schema(example = "15002")
        @NotNull(message = "A pessoa é obrigatória")
        @Positive(message = "Id da pessoa inválido")
        Long pessoaId,

        @Schema(example = "CONTATADO_BEM",
                allowableValues = {"CONTATADO_BEM", "PRECISA_AJUDA", "NAO_ATENDEU", "DADOS_INCORRETOS"},
                description = "Valor fora da lista gera 422 com os resultados válidos")
        @NotBlank(message = "O resultado é obrigatório")
        String resultado,

        @Schema(example = "Está bem, bebendo água. Filha passa à tarde.", nullable = true)
        @Size(max = 1000, message = "A observação deve ter no máximo 1000 caracteres")
        String observacao
) {
}
