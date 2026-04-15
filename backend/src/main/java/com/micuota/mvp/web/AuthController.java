package com.micuota.mvp.web;

import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.UserRepository;
import com.micuota.mvp.service.AuthSessionService;
import com.micuota.mvp.service.AuthResponse;
import com.micuota.mvp.service.LoginRequest;
import com.micuota.mvp.service.RegisterTenantRequest;
import com.micuota.mvp.service.TenantAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
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
    public AuthResponse registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        return tenantAuthService.registerTenant(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return tenantAuthService.login(request);
    }

    @GetMapping("/me")
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
