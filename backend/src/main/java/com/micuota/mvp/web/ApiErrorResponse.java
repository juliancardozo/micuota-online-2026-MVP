package com.micuota.mvp.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiErrorResponse", description = "Respuesta estandar de error de la API")
public record ApiErrorResponse(
    @Schema(example = "2026-04-15T12:30:00Z") String timestamp,
    @Schema(example = "Operacion permitida solo para TENANT_ADMIN") String error,
    @Schema(example = "Validation details", nullable = true) String details
) {
}
