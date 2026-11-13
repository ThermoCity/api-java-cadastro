package br.com.fiap.ThermoCity.security.dto;

import br.com.fiap.ThermoCity.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Usuário cadastrado. Senha e CPF nunca são devolvidos.")
public record UsuarioResponseDTO(
        @Schema(example = "1") Long id,
        @Schema(example = "Maria Silva") String nome,
        @Schema(example = "maria@exemplo.com") String email,
        @Schema(example = "18999991234") String telefone,
        @Schema(example = "19010000") String cep,
        @Schema(example = "-22.1228739", nullable = true) Double latitude,
        @Schema(example = "-51.3897085", nullable = true) Double longitude,
        @Schema(description = "false quando os serviços de CEP não devolveram coordenadas", example = "true")
        boolean localizacaoEncontrada,
        @Schema(example = "CIDADAO") String papel
) {
    public static UsuarioResponseDTO from(User user) {
        return new UsuarioResponseDTO(
                user.getIdUsuario(),
                user.getNome(),
                user.getEmail(),
                user.getTelefone(),
                user.getCep(),
                user.getLatitude(),
                user.getLongitude(),
                user.getLatitude() != null && user.getLongitude() != null,
                user.getPapel().name()
        );
    }
}
