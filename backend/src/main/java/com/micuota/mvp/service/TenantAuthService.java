package com.micuota.mvp.service;

import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.text.Normalizer;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final AuthSessionService authSessionService;
    private final CrmLeadService crmLeadService;
    private final OnboardingEmailService onboardingEmailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public TenantAuthService(
        TenantRepository tenantRepository,
        UserRepository userRepository,
        TeacherProfileRepository teacherProfileRepository,
        AuthSessionService authSessionService,
        CrmLeadService crmLeadService,
        OnboardingEmailService onboardingEmailService
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.authSessionService = authSessionService;
        this.crmLeadService = crmLeadService;
        this.onboardingEmailService = onboardingEmailService;
    }

    @Transactional
    public AuthResponse registerTenant(RegisterTenantRequest request) {
        throw new IllegalArgumentException("El alta de tenants esta restringida al administrador de plataforma");
    }

    /**
     * Alta autoservicio: se llama solo despues de que la persona demostro ser duena del email con un codigo.
     * Crea una cuenta individual (rol TEACHER) sin contrasena; entra siempre con codigo por email.
     */
    @Transactional
    public AuthResponse createSelfServiceTenant(String fullName, String email, String phone, String activity) {
        String base = slugBase(fullName);
        String slug = null;
        for (int i = 0; i < 10 && slug == null; i++) {
            String candidate = base + "-" + (100 + random.nextInt(900));
            if (tenantRepository.findBySlug(candidate).isEmpty()) {
                slug = candidate;
            }
        }
        if (slug == null) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        AuthResponse response = createTenant(fullName, slug, fullName, email, UUID.randomUUID().toString(), null, UserRole.TEACHER);
        String source = "SELF_SIGNUP" + (activity == null || activity.isBlank() ? "" : "_" + activity.trim().toUpperCase(Locale.ROOT));
        crmLeadService.captureInterestedLead(new CreateLeadRequest(email, phone, fullName, source));
        return response;
    }

    @Transactional
    public AuthResponse signIn(User user) {
        String token = authSessionService.createSession(user.getTenant().getId(), user.getId(), user.getRole());
        return new AuthResponse(token, user.getTenant().getId(), user.getTenant().getSlug(), user.getId(), user.getRole(), resolveDashboardUrl(user.getRole(), token));
    }

    @Transactional
    public AdminTenantProvisioningResult createTenantFromPlatform(AdminCreateTenantRequest request) {
        AuthResponse response = createTenant(
            request.tenantName(),
            request.tenantSlug(),
            request.adminFullName(),
            request.adminEmail(),
            request.adminPassword(),
            null,
            UserRole.TENANT_ADMIN
        );
        return new AdminTenantProvisioningResult(
            response.tenantId(),
            response.tenantSlug(),
            response.userId(),
            request.adminEmail().toLowerCase(Locale.ROOT),
            response.backofficeUrl()
        );
    }

    private AuthResponse createTenant(
        String tenantName,
        String tenantSlug,
        String fullName,
        String email,
        String password,
        String mpAccessToken,
        UserRole role
    ) {
        String slug = normalizeSlug(tenantSlug == null || tenantSlug.isBlank() ? tenantName : tenantSlug);
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new IllegalArgumentException("El slug del tenant ya existe");
        }

        Tenant tenant = new Tenant();
        tenant.setName(tenantName);
        tenant.setSlug(slug);
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setPlanCode("BASE");
        tenant.setTakeRateBps(350);
        tenant.setAdvancedDunningFeeBps(120);
        tenant.setRecoveryAutomationEnabled(false);
        tenant.setAdvancedAnalyticsEnabled(false);
        tenant.setIntegrationsEnabled(false);
        tenant = tenantRepository.save(tenant);

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (userRepository.findByTenantIdAndEmail(tenant.getId(), normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("El email ya esta registrado en este tenant");
        }

        User admin = new User();
        admin.setTenant(tenant);
        admin.setEmail(normalizedEmail);
        admin.setFullName(fullName);
        admin.setRole(role);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin = userRepository.save(admin);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(admin);
        profile.setDisplayName(fullName);
        profile.setMpAccessToken(mpAccessToken);
        teacherProfileRepository.save(profile);

        String token = authSessionService.createSession(tenant.getId(), admin.getId(), admin.getRole());
        String dashboardUrl = resolveDashboardUrl(admin.getRole(), token);

        onboardingEmailService.sendNewTenantWelcomeEmail(
            email,
            fullName,
            tenantName,
            tenant.getSlug()
        );

        return new AuthResponse(token, tenant.getId(), tenant.getSlug(), admin.getId(), admin.getRole(), dashboardUrl);
    }

    @Transactional
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
            case TENANT_ADMIN -> "/backoffice.html?token=" + token;
            case ADMIN -> "/admin.html?token=" + token;
            case TEACHER -> "/profesor.html?token=" + token;
            case STUDENT -> "/alumno.html?token=" + token;
        };
    }

    private String slugBase(String name) {
        try {
            String slug = normalizeSlug(name);
            return slug.length() > 40 ? slug.substring(0, 40).replaceAll("-$", "") : slug;
        } catch (IllegalArgumentException ex) {
            return "cuenta";
        }
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
