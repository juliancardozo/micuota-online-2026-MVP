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
    private final CrmLeadService crmLeadService;
    private final OnboardingEmailService onboardingEmailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

    @Transactional
    public AdminTenantProvisioningResult createTenantFromPlatform(AdminCreateTenantRequest request) {
        AuthResponse response = createTenant(
            request.tenantName(),
            request.tenantSlug(),
            request.adminFullName(),
            request.adminEmail(),
            request.adminPassword(),
            null,
            false
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
        boolean captureLead
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
        admin.setRole(UserRole.TENANT_ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin = userRepository.save(admin);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(admin);
        profile.setDisplayName(fullName);
        profile.setMpAccessToken(mpAccessToken);
        teacherProfileRepository.save(profile);

        String token = authSessionService.createSession(tenant.getId(), admin.getId(), admin.getRole());
        String dashboardUrl = resolveDashboardUrl(admin.getRole(), token);

        if (captureLead) {
            CrmLeadService.LeadCaptureResult leadCaptureResult = crmLeadService.captureInterestedLead(
                new CreateLeadRequest(
                    email,
                    null,
                    fullName,
                    "LANDING_TENANT_REGISTRATION"
                )
            );

            if (leadCaptureResult.newLead()) {
                onboardingEmailService.sendNewLeadCampaignEmail(leadCaptureResult.lead());
            }
        }
        onboardingEmailService.sendNewTenantWelcomeEmail(
            email,
            fullName,
            tenantName,
            tenant.getSlug(),
            dashboardUrl
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
