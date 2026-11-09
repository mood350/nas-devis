package com.nasimmobilier.devis.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Nasim Immobilier - Microservice de devis BTP")
                .version("0.0.1")
                .description("Catalogue, prix, devis et exports PDF/DOCX. Aucune authentification pour l'instant."));
    }
}
