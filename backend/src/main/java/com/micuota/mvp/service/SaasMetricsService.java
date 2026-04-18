package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class SaasMetricsService {

    private final MeterRegistry meterRegistry;

    public SaasMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordLeadCaptured(String source, boolean isNewLead) {
        Counter.builder("micuota_leads_captured_total")
            .description("Leads capturados por el CRM del SaaS")
            .tag("source", safe(source))
            .tag("kind", isNewLead ? "new" : "updated")
            .register(meterRegistry)
            .increment();
    }

    public void recordPaymentCreated(PaymentProviderType provider, PaymentFlowType flowType, BigDecimal amount) {
        Counter.builder("micuota_payments_created_total")
            .description("Pagos creados por el SaaS")
            .tag("provider", provider.name())
            .tag("flow_type", flowType.name())
            .register(meterRegistry)
            .increment();

        DistributionSummary.builder("micuota_payments_created_amount")
            .description("Monto de pagos creados")
            .baseUnit("currency")
            .tag("provider", provider.name())
            .tag("flow_type", flowType.name())
            .register(meterRegistry)
            .record(amount == null ? 0D : amount.doubleValue());
    }

    public void recordPaymentStatusChanged(OperationStatus status) {
        Counter.builder("micuota_payment_status_changes_total")
            .description("Cambios de estado de pagos")
            .tag("status", status.name())
            .register(meterRegistry)
            .increment();
    }

    public void recordSessionStarted(String role) {
        Counter.builder("micuota_sessions_started_total")
            .description("Sesiones iniciadas por rol")
            .tag("role", safe(role))
            .register(meterRegistry)
            .increment();
    }

    public void recordSessionEnded(String role, long durationSeconds) {
        Counter.builder("micuota_sessions_ended_total")
            .description("Sesiones cerradas por rol")
            .tag("role", safe(role))
            .register(meterRegistry)
            .increment();

        DistributionSummary.builder("micuota_session_duration_seconds")
            .description("Duracion de sesiones cerradas")
            .baseUnit("seconds")
            .tag("role", safe(role))
            .register(meterRegistry)
            .record(Math.max(0, durationSeconds));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase();
    }
}
