package com.micuota.mvp.web;

import com.micuota.mvp.service.CreateLeadRequest;
import com.micuota.mvp.service.CrmLeadService;
import com.micuota.mvp.service.OnboardingEmailService;
import com.micuota.mvp.service.PublicLeadCaptureRequest;
import com.micuota.mvp.service.PublicLeadCaptureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/leads")
@Tag(name = "Public Leads", description = "Captura publica de leads interesados desde la landing")
public class PublicLeadController {

    private final CrmLeadService crmLeadService;
    private final OnboardingEmailService onboardingEmailService;

    public PublicLeadController(CrmLeadService crmLeadService, OnboardingEmailService onboardingEmailService) {
        this.crmLeadService = crmLeadService;
        this.onboardingEmailService = onboardingEmailService;
    }

    @PostMapping("/interested")
    @Operation(summary = "Capturar interesado", description = "Guarda un lead de la landing en CRM y dispara onboarding por email para nuevos leads.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lead capturado"),
        @ApiResponse(responseCode = "422", description = "Payload invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PublicLeadCaptureResponse captureInterestedLead(@Valid @RequestBody PublicLeadCaptureRequest request) {
        String source = request.source() == null || request.source().isBlank()
            ? "LANDING_INTERESTED_LIST"
            : request.source().trim().toUpperCase();

        CrmLeadService.LeadCaptureResult result = crmLeadService.captureInterestedLead(
            new CreateLeadRequest(
                request.email(),
                request.phone(),
                request.fullName(),
                source
            )
        );

        if (result.newLead()) {
            onboardingEmailService.sendNewLeadCampaignEmail(result.lead());
        }

        return new PublicLeadCaptureResponse(
            result.lead().id(),
            result.lead().email(),
            result.lead().status(),
            result.newLead(),
            result.newLead() ? "Te agregamos a la lista de interesados." : "Ya estabas en la lista. Actualizamos tus datos."
        );
    }
}
