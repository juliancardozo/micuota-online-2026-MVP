package com.micuota.mvp.config;

import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja un unico administrador de la plataforma (rol ADMIN) con el email y la contraseña configurados.
 * La contraseña viene solo de la variable APP_PLATFORM_ADMIN_PASSWORD: sin ella no se toca nada.
 * Cualquier otro ADMIN pierde el rol y queda con una contraseña aleatoria.
 * El administrador vive en su propio tenant de plataforma, que no cuenta en las metricas ni se borra al limpiar.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PlatformAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminSeeder.class);
    public static final String PLATFORM_TENANT_SLUG = "micuota";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final String email;
    private final String password;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PlatformAdminSeeder(
        UserRepository userRepository,
        TenantRepository tenantRepository,
        @Value("${app.platform-admin.email:admin@micuota.online}") String email,
        @Value("${app.platform-admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) {
            log.warn("APP_PLATFORM_ADMIN_PASSWORD no esta configurada: no hay administrador de la plataforma");
            return;
        }

        User admin = userRepository.findAllByRole(UserRole.ADMIN).stream()
            .filter(user -> user.getEmail().equalsIgnoreCase(email))
            .findFirst()
            .or(() -> userRepository.findAllByEmailOrderByIdAsc(email).stream()
                .filter(user -> PLATFORM_TENANT_SLUG.equals(user.getTenant().getSlug()))
                .findFirst())
            .orElseGet(this::newAdmin);
        Tenant platformTenant = platformTenant();
        if (admin.getTenant() == null || !platformTenant.getId().equals(admin.getTenant().getId())) {
            admin.setTenant(platformTenant);
        }
        admin.setRole(UserRole.ADMIN);
        if (admin.getPasswordHash() == null || !admin.getPasswordHash().startsWith("$2")
            || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(password));
        }
        User saved = userRepository.save(admin);

        for (User other : userRepository.findAllByRole(UserRole.ADMIN)) {
            if (!other.getId().equals(saved.getId())) {
                other.setRole(UserRole.TENANT_ADMIN);
                other.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                userRepository.save(other);
                log.info("Se quito el rol ADMIN a userId={}", other.getId());
            }
        }
        log.info("Administrador de la plataforma listo userId={}", saved.getId());
    }

    private User newAdmin() {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Administrador MiCuota");
        return user;
    }

    private Tenant platformTenant() {
        return tenantRepository.findBySlug(PLATFORM_TENANT_SLUG).orElseGet(() -> {
            Tenant t = new Tenant();
            t.setName("MiCuota (plataforma)");
            t.setSlug(PLATFORM_TENANT_SLUG);
            t.setCreatedAt(OffsetDateTime.now());
            t.setPlanCode("BASE");
            t.setTakeRateBps(350);
            t.setAdvancedDunningFeeBps(120);
            t.setRecoveryAutomationEnabled(false);
            t.setAdvancedAnalyticsEnabled(false);
            t.setIntegrationsEnabled(false);
            return tenantRepository.save(t);
        });
    }
}
