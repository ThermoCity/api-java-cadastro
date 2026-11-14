package br.com.fiap.ThermoCity.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

/**
 * Cadastro publico: sempre cria um usuario com papel CIDADAO.
 * CPF, telefone e CEP sao aceitos com ou sem mascara e gravados somente com digitos.
 */
@Schema(description = "Dados para cadastro de um cidadão. CPF, telefone e CEP aceitam valores com ou sem máscara.")
public record RegisterDTO(
        @Schema(example = "Maria Silva")
        @NotBlank(message = "O nome não pode ser nulo ou vazio")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String nome,

        @Schema(example = "maria@exemplo.com")
        @NotBlank(message = "O e-mail não pode ser nulo ou vazio")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @Schema(example = "senhaForte123", minLength = 8, maxLength = 72)
        @NotBlank(message = "A senha não pode ser nula ou vazia")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String senha,

        @Schema(example = "(18) 99999-1234")
        @NotBlank(message = "O telefone não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "Telefone inválido. Use DDD + número, ex.: (18) 99999-9999")
        String telefone,

        @Schema(example = "529.982.247-25", description = "CPF válido (dígitos verificadores são conferidos)")
        @NotBlank(message = "O CPF não pode ser nulo ou vazio")
        @CPF(message = "CPF inválido")
        String cpf,

        @Schema(example = "19010-000", description = "Usado para obter latitude e longitude automaticamente")
        @NotBlank(message = "O CEP não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000")
        String cep
) {
}
