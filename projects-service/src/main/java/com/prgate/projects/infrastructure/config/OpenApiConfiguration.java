package com.prgate.projects.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI projectOpenApi(Environment environment) {
        boolean local=environment.acceptsProfiles(Profiles.of("local"));
        String name=local ? "localUser" : "cognito";
        var scheme=local ? new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                .name("X-User-Id").description("Solo desarrollo local: UUID local. La validación de usuarios está pendiente de integración")
                : new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("Access token de Cognito; no acepta ID tokens");
        return new OpenAPI().info(new Info().title("PRGate Projects API").version("v1")
                .description("Cursos, admisi?n y grupos acad?micos. Paginaci?n: page desde 0, size de 1 a 100. Errores RFC 9457."))
                .components(new Components().addSecuritySchemes(name,scheme))
                .addSecurityItem(new SecurityRequirement().addList(name));
    }
}
