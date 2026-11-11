package com.prgate.ai.infrastructure.inbound.rest;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI aiOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("PRGate AI API").version("v1")
                        .description("Planes tecnicos, analisis registrados y feedback educativo"))
                .components(new Components().addSecuritySchemes("serviceBearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("Usa INTERNAL_SERVICE_TOKEN de ai-service/.env; pega solo el valor, sin Bearer")))
                .addSecurityItem(new SecurityRequirement().addList("serviceBearer"));
    }
}