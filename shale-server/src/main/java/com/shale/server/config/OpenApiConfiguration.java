package com.shale.server.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Shale Server API",
        version = "v1",
        description = "HTTP API for Shale clients. info.version is document metadata, not a URL version or a stability promise for every listed path. The stable selected Case reads use /api/v2/cases; see docs/web-api-azure-readiness.md for scope and compatibility policy. Protected endpoints use server-issued bearer tokens validated against durable sessions; tenant and actor come from server authority, not client input."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfiguration {
}
