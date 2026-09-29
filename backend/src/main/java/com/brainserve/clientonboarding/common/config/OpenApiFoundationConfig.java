package com.brainserve.clientonboarding.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Client Onboarding Platform API",
                version = "0.7.0-phase-6",
                description = "Tenant-scoped identity, client/project core, workflows, client portal, forms and secure assets."
        ),
        servers = @Server(url = "/", description = "Current host")
)
@SecurityScheme(
        name = "cookieAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "BOS_SESSION",
        description = "Opaque server-side session cookie; mutations also require X-XSRF-TOKEN."
)
public class OpenApiFoundationConfig {
}
