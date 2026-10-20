package com.micuota.mvp.web;

import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.UserRepository;
import com.micuota.mvp.service.AdminLoginRequest;
import com.micuota.mvp.service.AdminLoginThrottle;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.AuthResponse;
import com.micuota.mvp.service.EmailCodeAuthService;
import com.micuota.mvp.service.EmailCodeRequest;
import com.micuota.mvp.service.EmailCodeVerifyRequest;
import com.micuota.mvp.service.EmailCodeVerifyResponse;
import com.micuota.mvp.service.LoginRequest;
import com.micuota.mvp.service.RegisterTenantRequest;
import com.micuota.mvp.service.SessionActivityService;
import com.micuota.mvp.service.TenantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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
    private final SessionActivityService sessionActivityService;
    private final UserRepository userRepository;
    private final EmailCodeAuthService emailCodeAuthService;
    private final AdminLoginThrottle adminLoginThrottle;

    public AuthController(
        TenantAuthService tenantAuthService,
        AuthSessionService authSessionService,
        SessionActivityService sessionActivityService,
        UserRepository userRepository,
        EmailCodeAuthService emailCodeAuthService,
        AdminLoginThrottle adminLoginThrottle
    ) {
        this.tenantAuthService = tenantAuthService;
        this.authSessionService = authSessionService;
        this.sessionActivityService = sessionActivityService;
        this.userRepository = userRepository;
        this.emailCodeAuthService = emailCodeAuthService;
        this.adminLoginThrottle = adminLoginThrottle;
    }

    @PostMapping("/email-code")
    @Operation(
        summary = "Pedir codigo por email",
        description = "Manda un codigo de 6 digitos al email para entrar o crear cuenta sin contrasena. Vence en 10 minutos."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Codigo enviado"),
        @ApiResponse(responseCode = "400", description = "Demasiados pedidos o fallo el envio", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Map<String, Object> requestEmailCode(@Valid @RequestBody EmailCodeRequest request, HttpServletRequest httpRequest) {
        emailCodeAuthService.requestCode(request.email(), clientIp(httpRequest));
        return Map.of("sent", true);
    }

    @PostMapping("/email-code/verify")
    @Operation(
        summary = "Verificar codigo por email",
        description = "Valida el codigo. Si el email ya tiene cuenta inicia sesion; si no, crea la cuenta (requiere fullName). " +
            "Responde NEEDS_NAME o CHOOSE_ACCOUNT cuando falta un dato; el mismo codigo sirve para reenviar."
    )
    public EmailCodeVerifyResponse verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
        return emailCodeAuthService.verify(request);
    }

    // Para limitar intentos se usa la ultima IP de X-Forwarded-For (la agrega el proxy de Render):
    // la primera la puede inventar el cliente y saltarse el limite.
    private static String proxyObservedIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return String.valueOf(request.getRemoteAddr());
        }
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].trim();
    }

    private static String clientIp(HttpServletRequest request) {
        // Render y Netlify ponen la IP real primera en X-Forwarded-For.
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null ? null : ip.substring(0, Math.min(ip.length(), 64));
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

    @PostMapping("/admin-login")
    @Operation(
        summary = "Login del administrador de la plataforma",
        description = "Autentica al unico usuario ADMIN por email + password. Limitado a 5 intentos fallidos por IP cada 15 minutos."
    )
    public AuthResponse adminLogin(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        String key = proxyObservedIp(httpRequest);
        adminLoginThrottle.checkAllowed(key);
        try {
            AuthResponse response = tenantAuthService.adminLogin(request);
            adminLoginThrottle.reset(key);
            return response;
        } catch (IllegalArgumentException exception) {
            adminLoginThrottle.recordFailure(key);
            throw exception;
        }
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
        sessionActivityService.touchSession(token, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        User user = userRepository.findById(session.userId())
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para sesion"));

        return new AuthMeResponse(
            user.getId(),
            session.tenantId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole(),
            user.getTenant().getName(),
            user.getTenant().getSlug()
        );
    }

    public record AuthMeResponse(Long userId, Long tenantId, String fullName, String email, UserRole role, String tenantName, String tenantSlug) {
    }

    @PostMapping("/heartbeat")
    @Operation(
        summary = "Heartbeat de sesion",
        description = "Actualiza lastSeenAt y duracion parcial de la sesion autenticada.",
        security = @SecurityRequirement(name = "AuthToken")
    )
    public AckResponse heartbeat(@RequestHeader("X-Auth-Token") String token) {
        authSessionService.requireSession(token);
        sessionActivityService.touchSession(token, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return new AckResponse("heartbeat-ok");
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Cerrar sesion",
        description = "Marca endedAt y durationSeconds para la sesion autenticada.",
        security = @SecurityRequirement(name = "AuthToken")
    )
    public AckResponse logout(@RequestHeader("X-Auth-Token") String token) {
        authSessionService.requireSession(token);
        sessionActivityService.endSession(token, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return new AckResponse("logout-ok");
    }

    public record AckResponse(String status) {
    }
}
