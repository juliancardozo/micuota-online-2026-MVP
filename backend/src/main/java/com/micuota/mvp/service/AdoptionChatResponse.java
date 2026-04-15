package com.micuota.mvp.service;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdoptionChatResponse", description = "Salida del chatbot de adopcion MiCuota")
public record AdoptionChatResponse(
    @Schema(description = "Respuesta estructurada para mostrar en el chat")
    String answer,
    @Schema(example = "profesor")
    String detectedRole,
    @Schema(example = "suscripcion")
    String recommendedFlow,
    @Schema(example = "backend-contextual")
    String source
) {
}
