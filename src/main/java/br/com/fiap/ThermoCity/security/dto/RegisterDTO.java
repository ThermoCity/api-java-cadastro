package br.com.fiap.ThermoCity.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

/**
 * Cadastro publico: sempre cria um usuario com papel CIDADAO.
 * CPF, telefone e CEP sao aceitos com ou sem mascara e gravados somente com digitos.
 */
public record RegisterDTO(
        @NotBlank(message = "O nome não pode ser nulo ou vazio")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String nome,

        @NotBlank(message = "O e-mail não pode ser nulo ou vazio")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "A senha não pode ser nula ou vazia")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String senha,

        @NotBlank(message = "O telefone não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "Telefone inválido. Use DDD + número, ex.: (18) 99999-9999")
        String telefone,

        @NotBlank(message = "O CPF não pode ser nulo ou vazio")
        @CPF(message = "CPF inválido")
        String cpf,

        @NotBlank(message = "O CEP não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000")
        String cep
) {
}
