package com.micuota.mvp.web;

import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.UserRepository;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.AuthResponse;
import com.micuota.mvp.service.LoginRequest;
import com.micuota.mvp.service.RegisterTenantRequest;
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
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Autenticacion multi-tenant y sesion por token")
public class AuthController {

    private final TenantAuthService tenantAuthService;
    private final AuthSessionService authSessionService;
    private final UserRepository userRepository;

    public AuthController(
        TenantAuthService tenantAuthService,
        AuthSessionService authSessionService,
        UserRepository userRepository
    ) {
        this.tenantAuthService = tenantAuthService;
        this.authSessionService = authSessionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register-tenant")
    @Operation(
        summary = "Registrar tenant",
        description = "Crea un tenant, crea el usuario administrador inicial y devuelve token + URL de redireccion por rol."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant registrado"),
        @ApiResponse(responseCode = "400", description = "Error de negocio", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Payload invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthResponse registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        return tenantAuthService.registerTenant(request);
    }

    @PostMapping("/login")
    @Operation(
        summary = "Login",
        description = "Autentica por tenantSlug + email + password y devuelve token + URL del dashboard segun rol."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesion iniciada"),
        @ApiResponse(responseCode = "400", description = "Credenciales invalidas o tenant no encontrado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Payload invalido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return tenantAuthService.login(request);
    }

    @GetMapping("/me")
    @Operation(
        summary = "Perfil de sesion",
        description = "Devuelve el usuario autenticado a partir del header X-Auth-Token.",
        security = @SecurityRequirement(name = "AuthToken")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil recuperado"),
        @ApiResponse(responseCode = "400", description = "Token invalido o usuario inexistente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthMeResponse me(@RequestHeader("X-Auth-Token") String token) {
        AuthSessionService.SessionContext session = authSessionService.requireSession(token);
        User user = userRepository.findById(session.userId())
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para sesion"));

        return new AuthMeResponse(
            user.getId(),
            session.tenantId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole()
        );
    }

    public record AuthMeResponse(Long userId, Long tenantId, String fullName, String email, UserRole role) {
    }
}
