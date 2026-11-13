package br.com.fiap.ThermoCity.security.infra;

import br.com.fiap.ThermoCity.user.model.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class TokenService {

    private final Algorithm algorithm;
    private final String issuer;
    private final Duration validade;

    public TokenService(@Value("${api.security.token.secret}") String secret,
                        @Value("${api.security.token.issuer}") String issuer,
                        @Value("${api.security.token.expiration-hours}") long expirationHours) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.issuer = issuer;
        this.validade = Duration.ofHours(expirationHours);
    }

    public TokenGerado generateToken(User user) {
        Instant expiraEm = Instant.now().plus(validade);
        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(user.getUsername())
                .withClaim("papel", user.getPapel().name())
                .withClaim("idUsuario", user.getIdUsuario())
                .withIssuedAt(Instant.now())
                .withExpiresAt(expiraEm)
                .sign(algorithm);
        return new TokenGerado(token, expiraEm);
    }

    public Optional<String> validateToken(String token) {
        try {
            return Optional.of(JWT.require(algorithm).withIssuer(issuer).build().verify(token).getSubject());
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public record TokenGerado(String valor, Instant expiraEm) {
    }
}
