package br.com.fiap.ThermoCity.security.controller;

import br.com.fiap.ThermoCity.security.dto.AuthenticationDTO;
import br.com.fiap.ThermoCity.security.dto.LoginResponseDTO;
import br.com.fiap.ThermoCity.security.dto.RegisterDTO;
import br.com.fiap.ThermoCity.security.dto.UsuarioResponseDTO;
import br.com.fiap.ThermoCity.security.infra.TokenService;
import br.com.fiap.ThermoCity.user.model.User;
import br.com.fiap.ThermoCity.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;

    public AuthenticationController(AuthenticationManager authenticationManager, TokenService tokenService,
                                    UserService userService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthenticationDTO dto) {
        var credenciais = new UsernamePasswordAuthenticationToken(
                dto.email().trim().toLowerCase(Locale.ROOT), dto.senha());
        var auth = authenticationManager.authenticate(credenciais);

        User user = (User) auth.getPrincipal();
        var token = tokenService.generateToken(user);
        return ResponseEntity.ok(new LoginResponseDTO(token.valor(), "Bearer", user.getPapel().name(), token.expiraEm()));
    }

    @PostMapping("/register")
    public ResponseEntity<UsuarioResponseDTO> register(@RequestBody @Valid RegisterDTO dto) {
        User user = userService.registrarCidadao(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponseDTO.from(user));
    }
}
