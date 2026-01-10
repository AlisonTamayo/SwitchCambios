package com.bancario.msdirectorio.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Microservicio Directorio - API")
                        .version("1.0.0")
                        .description("Documentación para gestión de Instituciones y Reglas de Enrutamiento.")
                        .contact(new Contact()
                                .name("Equipo Bancario")
                                .email("dev@banco.com")));
    }
}