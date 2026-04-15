package com.micuota.mvp.service;

import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final AuthSessionService authSessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TenantAuthService(
        TenantRepository tenantRepository,
        UserRepository userRepository,
        TeacherProfileRepository teacherProfileRepository,
        AuthSessionService authSessionService
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public AuthResponse registerTenant(RegisterTenantRequest request) {
        String slug = normalizeSlug(request.tenantSlug() == null || request.tenantSlug().isBlank() ? request.tenantName() : request.tenantSlug());
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new IllegalArgumentException("El slug del tenant ya existe");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName());
        tenant.setSlug(slug);
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);

        if (userRepository.findByTenantIdAndEmail(tenant.getId(), request.email()).isPresent()) {
            throw new IllegalArgumentException("El email ya esta registrado en este tenant");
        }

        User admin = new User();
        admin.setTenant(tenant);
        admin.setEmail(request.email().toLowerCase(Locale.ROOT));
        admin.setFullName(request.fullName());
        admin.setRole(UserRole.TENANT_ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin = userRepository.save(admin);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(admin);
        profile.setDisplayName(request.fullName());
        profile.setMpAccessToken(request.mpAccessToken());
        teacherProfileRepository.save(profile);

        String token = authSessionService.createSession(tenant.getId(), admin.getId(), admin.getRole());
        return new AuthResponse(token, tenant.getId(), tenant.getSlug(), admin.getId(), admin.getRole(), resolveDashboardUrl(admin.getRole(), token));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Tenant tenant = tenantRepository.findBySlug(normalizeSlug(request.tenantSlug()))
            .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));

        User user = userRepository.findByTenantIdAndEmail(tenant.getId(), request.email().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas"));

        boolean valid;
        if (user.getPasswordHash() != null && user.getPasswordHash().startsWith("$2")) {
            valid = passwordEncoder.matches(request.password(), user.getPasswordHash());
        } else {
            valid = request.password().equals(user.getPasswordHash()) || request.password().equals(user.getPasswordHash().replace("{noop}", ""));
        }

        if (!valid) {
            throw new IllegalArgumentException("Credenciales invalidas");
        }

        String token = authSessionService.createSession(tenant.getId(), user.getId(), user.getRole());
        return new AuthResponse(token, tenant.getId(), tenant.getSlug(), user.getId(), user.getRole(), resolveDashboardUrl(user.getRole(), token));
    }

    private String resolveDashboardUrl(UserRole role, String token) {
        return switch (role) {
            case TENANT_ADMIN, ADMIN -> "/backoffice.html?token=" + token;
            case TEACHER -> "/profesor.html?token=" + token;
            case STUDENT -> "/alumno.html?token=" + token;
        };
    }

    private String normalizeSlug(String raw) {
        String normalized = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Slug de tenant invalido");
        }
        return normalized;
    }
}
