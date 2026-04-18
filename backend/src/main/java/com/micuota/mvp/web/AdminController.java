package com.micuota.mvp.web;

import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.service.AdminAnalyticsService;
import com.micuota.mvp.service.AdminSystemDashboardView;
import com.micuota.mvp.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminController(AuthSessionService authSessionService, AdminAnalyticsService adminAnalyticsService) {
        this.authSessionService = authSessionService;
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @GetMapping("/system-dashboard")
    @Operation(summary = "Dashboard global MiCuota", description = "Resume sesiones, cursos, ingresos, tendencias y leads del sistema completo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard generado"),
        @ApiResponse(responseCode = "400", description = "Token invalido o permisos insuficientes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminSystemDashboardView systemDashboard(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        if (session.role() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Operacion permitida solo para ADMIN");
        }
        return adminAnalyticsService.buildSystemDashboard();
    }
}
