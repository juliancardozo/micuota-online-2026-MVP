package com.micuota.mvp.web;

import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.service.AdminCreateTenantRequest;
import com.micuota.mvp.service.AdminAnalyticsService;
import com.micuota.mvp.service.AdminBillingService;
import com.micuota.mvp.service.AdminTenantBillingView;
import com.micuota.mvp.service.AdminOverviewView;
import com.micuota.mvp.service.AdminResetRequest;
import com.micuota.mvp.service.AdminResetResult;
import com.micuota.mvp.service.AdminTenantProvisioningResult;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.TenantAuthService;
import com.micuota.mvp.service.UpdateTenantBillingSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Dashboard global de MiCuota para administracion del SaaS")
@SecurityRequirement(name = "AuthToken")
public class AdminController {

    private final AuthSessionService authSessionService;
    private final AdminAnalyticsService adminAnalyticsService;
    private final TenantAuthService tenantAuthService;
    private final AdminBillingService adminBillingService;

    public AdminController(
        AuthSessionService authSessionService,
        AdminAnalyticsService adminAnalyticsService,
        TenantAuthService tenantAuthService,
        AdminBillingService adminBillingService
    ) {
        this.authSessionService = authSessionService;
        this.adminAnalyticsService = adminAnalyticsService;
        this.tenantAuthService = tenantAuthService;
        this.adminBillingService = adminBillingService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Resumen de la plataforma", description = "Cuentas, profesionales, conexiones de Mercado Pago, cobros y comision por moneda. Excluye la cuenta de plataforma.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard generado"),
        @ApiResponse(responseCode = "400", description = "Token invalido o permisos insuficientes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminOverviewView overview(@RequestHeader("X-Auth-Token") String token) {
        requirePlatformAdmin(token);
        return adminAnalyticsService.buildOverview();
    }

    @PostMapping("/tenants")
    @Operation(summary = "Crear tenant desde plataforma", description = "Provisiona un tenant y su TENANT_ADMIN inicial. Requiere ADMIN.")
    public AdminTenantProvisioningResult createTenant(
        @RequestHeader("X-Auth-Token") String token,
        @Valid @RequestBody AdminCreateTenantRequest request
    ) {
        requirePlatformAdmin(token);
        return tenantAuthService.createTenantFromPlatform(request);
    }

    @GetMapping("/tenants")
    @Operation(summary = "Comisiones por tenant", description = "Lista tenants con sus tasas de comision y lo cobrado via marketplace_fee. Requiere ADMIN.")
    public List<AdminTenantBillingView> listTenants(@RequestHeader("X-Auth-Token") String token) {
        requirePlatformAdmin(token);
        return adminBillingService.listTenants();
    }

    @PutMapping("/tenants/{tenantId}/billing")
    @Operation(summary = "Editar comision del tenant", description = "Cambia plan y tasas (en puntos basicos) que se aplican a los cobros nuevos. Requiere ADMIN.")
    public AdminTenantBillingView updateTenantBilling(
        @RequestHeader("X-Auth-Token") String token,
        @PathVariable Long tenantId,
        @Valid @RequestBody UpdateTenantBillingSettingsRequest request
    ) {
        requirePlatformAdmin(token);
        return adminBillingService.updateBilling(tenantId, request);
    }

    @PostMapping("/maintenance/reset")
    @Operation(
        summary = "Limpiar datos",
        description = "Borra todas las cuentas salvo keepTenantSlugs (la de plataforma se conserva siempre) y, con deleteAllPayments, tambien todos los cobros. Irreversible. Requiere ADMIN."
    )
    public AdminResetResult reset(
        @RequestHeader("X-Auth-Token") String token,
        @RequestBody AdminResetRequest request
    ) {
        requirePlatformAdmin(token);
        return adminAnalyticsService.resetData(request);
    }

    private void requirePlatformAdmin(String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        if (session.role() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Operacion permitida solo para ADMIN");
        }
    }
}
