package br.com.fiap.ThermoCity.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Troca de senha do próprio usuário")
public record AlterarSenhaDTO(
        @Schema(example = "senhaForte123")
        @NotBlank(message = "A senha atual é obrigatória")
        String senhaAtual,

        @Schema(example = "outraSenhaForte456", minLength = 8, maxLength = 72)
        @NotBlank(message = "A nova senha não pode ser nula ou vazia")
        @Size(min = 8, max = 72, message = "A nova senha deve ter entre 8 e 72 caracteres")
        String novaSenha
) {
}
