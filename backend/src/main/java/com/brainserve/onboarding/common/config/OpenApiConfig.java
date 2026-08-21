package com.brainserve.onboarding.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI clientOnboardingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Client Onboarding Platform API")
                        .version("v1")
                        .description("API through Phase 2: identity/authentication, organization tenancy, permission-based RBAC, clients, contacts, service catalog, projects, project members, and activity timelines."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
