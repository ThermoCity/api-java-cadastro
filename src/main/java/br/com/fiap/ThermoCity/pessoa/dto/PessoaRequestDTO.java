package br.com.fiap.ThermoCity.pessoa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = """
        Dados da pessoa acompanhada (60 anos ou mais). Usado no cadastro (POST) e na alteração (PUT, \
        que substitui todos os campos). Setor censitário e UBS de referência são calculados pela coordenada.""")
public record PessoaRequestDTO(

        @Schema(example = "Dona Aparecida Souza")
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,

        @Schema(example = "1948-03-15", description = "Precisa resultar em 60 anos ou mais")
        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        @Schema(example = "F", allowableValues = {"F", "M"}, nullable = true)
        @Pattern(regexp = "^[FM]$", message = "Sexo deve ser F ou M")
        String sexo,

        @Schema(example = "(18) 99999-1234")
        @NotBlank(message = "O telefone é obrigatório")
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "Telefone inválido. Use DDD + número, ex.: (18) 99999-1234")
        String telefone,

        @Schema(example = "Rua Siqueira Campos, 1200 - Centro")
        @NotBlank(message = "O endereço é obrigatório")
        @Size(max = 200, message = "O endereço deve ter no máximo 200 caracteres")
        String endereco,

        @Schema(example = "19010-000", nullable = true)
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000")
        String cep,

        @Schema(example = "-22.1228739", description = "Latitude do endereço (o app obtém pelo GPS ou pelo mapa)")
        @NotNull(message = "A latitude é obrigatória")
        @DecimalMin(value = "-90.0", message = "Latitude deve estar entre -90 e 90")
        @DecimalMax(value = "90.0", message = "Latitude deve estar entre -90 e 90")
        Double latitude,

        @Schema(example = "-51.3897085")
        @NotNull(message = "A longitude é obrigatória")
        @DecimalMin(value = "-180.0", message = "Longitude deve estar entre -180 e 180")
        @DecimalMax(value = "180.0", message = "Longitude deve estar entre -180 e 180")
        Double longitude,

        @Schema(example = "true", defaultValue = "false")
        Boolean moraSozinho,

        @Schema(example = "false", defaultValue = "false")
        Boolean mobilidadeReduzida,

        @Schema(example = "true", defaultValue = "false", description = "Hipertensão, diabetes, cardiopatia etc.")
        Boolean condicaoCronica,

        @Schema(example = "Carlos Souza (filho)", nullable = true)
        @Size(max = 120, message = "O nome do contato de referência deve ter no máximo 120 caracteres")
        String contatoReferenciaNome,

        @Schema(example = "(18) 98888-7777", nullable = true)
        @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$",
                message = "Telefone do contato de referência inválido")
        String contatoReferenciaTelefone,

        @Schema(example = "true", description = "Consentimento LGPD. Obrigatório e precisa ser true")
        Boolean consentimentoLgpd
) {
}
