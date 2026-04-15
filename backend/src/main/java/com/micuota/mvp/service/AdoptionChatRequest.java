package com.micuota.mvp.service;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AdoptionChatRequest", description = "Entrada del chatbot de adopcion MiCuota")
public record AdoptionChatRequest(
    @NotBlank
    @Schema(example = "Soy profesor y no se si conviene suscripcion o pago unico")
    String message,
    @Schema(example = "/landing.html")
    String page,
    @Schema(example = "profesor")
    String roleHint,
    @Schema(example = "true", description = "Si es true devuelve respuesta corta de chat rapido")
    Boolean quickMode
) {
}
