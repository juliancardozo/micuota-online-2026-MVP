package com.micuota.mvp.web;

import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.service.AdminCreateTenantRequest;
import com.micuota.mvp.service.AdminAnalyticsService;
import com.micuota.mvp.service.AdminSystemDashboardView;
import com.micuota.mvp.service.AdminTenantCleanupResult;
import com.micuota.mvp.service.AdminTenantProvisioningResult;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.TenantAuthService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Dashboard global de MiCuota para administracion del SaaS")
@SecurityRequirement(name = "AuthToken")
public class AdminController {

    private final AuthSessionService authSessionService;
    private final AdminAnalyticsService adminAnalyticsService;
    private final TenantAuthService tenantAuthService;

    public AdminController(
        AuthSessionService authSessionService,
        AdminAnalyticsService adminAnalyticsService,
        TenantAuthService tenantAuthService
    ) {
        this.authSessionService = authSessionService;
        this.adminAnalyticsService = adminAnalyticsService;
        this.tenantAuthService = tenantAuthService;
    }

    @GetMapping("/system-dashboard")
    @Operation(summary = "Dashboard global MiCuota", description = "Resume sesiones, cursos, ingresos, tendencias y leads del sistema completo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard generado"),
        @ApiResponse(responseCode = "400", description = "Token invalido o permisos insuficientes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminSystemDashboardView systemDashboard(@RequestHeader("X-Auth-Token") String token) {
        requirePlatformAdmin(token);
        return adminAnalyticsService.buildSystemDashboard();
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

    @PostMapping("/maintenance/purge-non-demo-tenants")
    @Operation(summary = "Purgar tenants no demo", description = "Elimina tenants distintos de demo-academia y sus datos dependientes. Requiere ADMIN.")
    public AdminTenantCleanupResult purgeNonDemoTenants(@RequestHeader("X-Auth-Token") String token) {
        requirePlatformAdmin(token);
        return adminAnalyticsService.purgeTenantsExcept("demo-academia");
    }

    private void requirePlatformAdmin(String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        if (session.role() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Operacion permitida solo para ADMIN");
        }
    }
}
