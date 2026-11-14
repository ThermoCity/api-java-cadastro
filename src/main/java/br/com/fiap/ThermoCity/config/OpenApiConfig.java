package br.com.fiap.ThermoCity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI thermoCityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ThermoCity — API Java (camada QUEM)")
                        .version("v1")
                        .description("""
                                Gêmeo digital de risco de calor para Presidente Prudente-SP.
                                Esta API emite os tokens JWT de todo o ecossistema ThermoCity e transforma o nível \
                                de calor (QUANDO) e o território (ONDE) numa lista de pessoas que cada agente de \
                                saúde deve contatar primeiro (QUEM).

                                **Como autenticar:** faça `POST /auth/login`, copie o campo `token` da resposta, \
                                clique em **Authorize** e cole o token (sem o prefixo `Bearer`).""")
                        .contact(new Contact().name("Equipe ThermoCity — FIAP Global Solution 2026/2")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT RS256 obtido em POST /auth/login")))
                // Por padrão todos os endpoints exigem token; os públicos sobrescrevem com @SecurityRequirements()
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
