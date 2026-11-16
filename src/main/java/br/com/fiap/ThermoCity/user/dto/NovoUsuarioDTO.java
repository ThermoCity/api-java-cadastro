package br.com.fiap.ThermoCity.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

@Schema(description = "Usuário criado por um gestor: agente de saúde, outro gestor ou o coletor da API .NET")
public record NovoUsuarioDTO(
        @Schema(example = "Joana Agente")
        @NotBlank(message = "O nome não pode ser nulo ou vazio")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String nome,

        @Schema(example = "joana.agente@saude.prudente.sp.gov.br")
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

        @Schema(example = "111.444.777-35")
        @NotBlank(message = "O CPF não pode ser nulo ou vazio")
        @CPF(message = "CPF inválido")
        String cpf,

        @Schema(example = "19010-000")
        @NotBlank(message = "O CEP não pode ser nulo ou vazio")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000")
        String cep,

        @Schema(example = "AGENTE", allowableValues = {"AGENTE", "GESTOR", "COLETOR"})
        @NotBlank(message = "O papel é obrigatório")
        @Pattern(regexp = "^(AGENTE|GESTOR|COLETOR)$",
                message = "Papel inválido. Use AGENTE, GESTOR ou COLETOR (cidadãos usam /auth/cadastro)")
        String papel,

        @Schema(example = "CNES-2755114", description = "UBS de atuação. Obrigatória para AGENTE", nullable = true)
        String ubsId
) {
}
