package br.com.fiap.ThermoCity.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados que o próprio usuário pode alterar no seu cadastro.
 * CPF, papel e UBS de atuação não mudam por aqui; a senha tem endpoint próprio.
 */
@Schema(description = "Dados do próprio cadastro. CPF, papel e UBS não podem ser alterados por aqui.")
public record AtualizarUsuarioDTO(
        @Schema(example = "Maria Silva")
        @NotBlank(message = "O nome não pode ser nulo ou vazio")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String nome,

        @Schema(example = "maria@exemplo.com", description = "Se mudar, o token atual deixa de valer e é preciso logar de novo")
        @NotBlank(message = "O e-mail não pode ser nulo ou vazio")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(example = "(18) 99999-1234")
        @NotBlank(message = "O telefone não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "Telefone inválido. Use DDD + número, ex.: (18) 99999-9999")
        String telefone,

        @Schema(example = "19010-000", description = "Se mudar, latitude e longitude são recalculadas")
        @NotBlank(message = "O CEP não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000")
        String cep
) {
}
