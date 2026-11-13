package br.com.fiap.ThermoCity.security.dto;

import br.com.fiap.ThermoCity.user.model.User;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        String cep,
        Double latitude,
        Double longitude,
        boolean localizacaoEncontrada,
        String papel
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
