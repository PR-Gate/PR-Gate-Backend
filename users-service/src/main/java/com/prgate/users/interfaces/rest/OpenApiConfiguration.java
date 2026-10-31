package com.prgate.users.interfaces.rest;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI usersOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PRGate Users API")
                        .description("API del microservicio de usuarios de PRGate")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("serviceBearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Token de servicio compartido con el microservicio de Proyectos")));
    }
}
