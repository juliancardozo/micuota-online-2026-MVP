package com.micuota.mvp.config;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.LeadRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.SessionActivityRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaasMetricsConfig {

    public SaasMetricsConfig(
        MeterRegistry meterRegistry,
        TenantRepository tenantRepository,
        UserRepository userRepository,
        CourseRepository courseRepository,
        LeadRepository leadRepository,
        PaymentOperationRepository paymentOperationRepository,
        SessionActivityRepository sessionActivityRepository
    ) {
        Gauge.builder("micuota_saas_tenants_total", tenantRepository, TenantRepository::count)
            .description("Cantidad total de tenants del SaaS")
            .register(meterRegistry);

        Gauge.builder("micuota_saas_users_total", userRepository, UserRepository::count)
            .description("Cantidad total de usuarios del SaaS")
            .register(meterRegistry);

        Gauge.builder("micuota_saas_courses_total", courseRepository, CourseRepository::count)
            .description("Cantidad total de cursos del SaaS")
            .register(meterRegistry);

        Gauge.builder("micuota_saas_leads_total", leadRepository, LeadRepository::count)
            .description("Cantidad total de leads del SaaS")
            .register(meterRegistry);

        Gauge.builder("micuota_saas_active_sessions", sessionActivityRepository,
                repo -> repo.countByLastSeenAtGreaterThanEqualAndEndedAtIsNull(OffsetDateTime.now().minusMinutes(5)))
            .description("Sesiones activas aproximadas en los ultimos 5 minutos")
            .register(meterRegistry);

        Gauge.builder("micuota_saas_revenue_success", paymentOperationRepository,
                repo -> repo.findAll().stream()
                    .filter(payment -> payment.getStatus() == OperationStatus.SUCCESS)
                    .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .doubleValue())
            .description("Revenue acumulado cobrado con estado SUCCESS")
            .baseUnit("currency")
            .register(meterRegistry);
    }
}
