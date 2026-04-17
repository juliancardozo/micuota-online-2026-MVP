package com.micuota.mvp.web;

import com.micuota.mvp.service.AssignLeadRequest;
import com.micuota.mvp.service.CreateLeadRequest;
import com.micuota.mvp.service.CrmApiKeyService;
import com.micuota.mvp.service.CrmLeadService;
import com.micuota.mvp.service.LeadSearchResponse;
import com.micuota.mvp.service.LeadView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crm")
@Tag(name = "CRM", description = "Mini CRM interno para capturar, buscar y asignar leads del MVP")
@SecurityRequirement(name = "CrmBearerAuth")
public class CrmController {

    private final CrmApiKeyService crmApiKeyService;
    private final CrmLeadService crmLeadService;

    public CrmController(CrmApiKeyService crmApiKeyService, CrmLeadService crmLeadService) {
        this.crmApiKeyService = crmApiKeyService;
        this.crmLeadService = crmLeadService;
    }

    @PostMapping("/leads")
    @Operation(summary = "Guardar lead", description = "Crea o actualiza un lead por email.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lead persistido"),
        @ApiResponse(responseCode = "400", description = "API key invalida o error de negocio", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Payload invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public LeadView saveLead(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody CreateLeadRequest request
    ) {
        crmApiKeyService.requireAuthorized(authorization);
        return crmLeadService.saveLead(request);
    }

    @GetMapping("/leads")
    @Operation(summary = "Listar o buscar leads", description = "Si se envia email, filtra por email. Si no, devuelve todos.")
    public LeadSearchResponse listLeads(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(required = false) String email
    ) {
        crmApiKeyService.requireAuthorized(authorization);
        return crmLeadService.searchByEmail(email);
    }

    @GetMapping("/leads/search")
    @Operation(summary = "Buscar lead por email", description = "Devuelve arreglo data compatible con workflows de n8n.")
    public LeadSearchResponse searchLead(
        @RequestHeader("Authorization") String authorization,
        @RequestParam String email
    ) {
        crmApiKeyService.requireAuthorized(authorization);
        return crmLeadService.searchByEmail(email);
    }

    @PostMapping("/leads/assign")
    @Operation(summary = "Asignar lead", description = "Actualiza score, segmento y owner sugerido.")
    public LeadView assignLead(
        @RequestHeader("Authorization") String authorization,
        @Valid @RequestBody AssignLeadRequest request
    ) {
        crmApiKeyService.requireAuthorized(authorization);
        return crmLeadService.assignLead(request);
    }
}
