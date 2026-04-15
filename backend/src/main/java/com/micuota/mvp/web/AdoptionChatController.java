package com.micuota.mvp.web;

import com.micuota.mvp.service.AdoptionChatRequest;
import com.micuota.mvp.service.AdoptionChatResponse;
import com.micuota.mvp.service.AdoptionChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "Asistente de adopcion MiCuota para onboarding comercial y funcional")
public class AdoptionChatController {

    private final AdoptionChatService adoptionChatService;

    public AdoptionChatController(AdoptionChatService adoptionChatService) {
        this.adoptionChatService = adoptionChatService;
    }

    @PostMapping("/adoption")
    @Operation(
        summary = "Asesor de adopcion",
        description = "Genera respuesta guiada para adopcion de MiCuota segun mensaje, pagina actual y contexto de sesion opcional.",
        security = @SecurityRequirement(name = "AuthToken")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Respuesta generada"),
        @ApiResponse(responseCode = "400", description = "Payload invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdoptionChatResponse adoptionAdvice(
        @RequestHeader(value = "X-Auth-Token", required = false) String token,
        @Valid @RequestBody AdoptionChatRequest request
    ) {
        return adoptionChatService.advise(token, request);
    }
}
