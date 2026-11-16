package br.com.fiap.ThermoCity.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciais de login")
public record AuthenticationDTO(
        @Schema(example = "maria@exemplo.com")
        @NotBlank(message = "O e-mail não pode ser nulo ou vazio")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(example = "senhaForte123")
        @NotBlank(message = "A senha não pode ser nula ou vazia")
        String senha
) {
}
