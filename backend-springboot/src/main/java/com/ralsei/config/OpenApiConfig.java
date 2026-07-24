package com.ralsei.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI / Swagger configuration with Bearer JWT for Try-it-out.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nha xe Ralsei API")
                        .description("REST API for coach passenger booking and cargo operations.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                                .name(BEARER_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
