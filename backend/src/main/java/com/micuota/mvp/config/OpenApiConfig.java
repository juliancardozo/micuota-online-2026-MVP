package com.micuota.mvp.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "MiCuota.online API",
        version = "v0.1.0",
        description = "API del MVP multi-tenant para autenticacion, backoffice, pagos y callbacks.",
        contact = @Contact(name = "MiCuota Team")
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local")
    }
)
@SecurityScheme(
    name = "AuthToken",
    type = SecuritySchemeType.APIKEY,
    in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER,
    paramName = "X-Auth-Token",
    description = "Token de sesion retornado por /api/auth/login o /api/auth/register-tenant. " +
        "Se envia en el header X-Auth-Token para endpoints privados."
)
@SecurityScheme(
    name = "CrmBearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "API Key",
    description = "API key tecnica para integraciones CRM y workflows de n8n."
)
public class OpenApiConfig {
}
