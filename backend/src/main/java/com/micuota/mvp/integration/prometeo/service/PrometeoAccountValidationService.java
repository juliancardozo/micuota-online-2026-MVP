package com.micuota.mvp.integration.prometeo.service;

import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationRequest;
import com.micuota.mvp.integration.prometeo.dto.PrometeoAccountValidationResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PrometeoAccountValidationService {

    private static final Logger log = LoggerFactory.getLogger(PrometeoAccountValidationService.class);

    private final PrometeoAccountValidationClient client;
    private final MeterRegistry meterRegistry;

    public PrometeoAccountValidationService(
        PrometeoAccountValidationClient client,
        MeterRegistry meterRegistry
    ) {
        this.client = client;
        this.meterRegistry = meterRegistry;
    }

    public PrometeoAccountValidationResponse validateAccount(
        Long tenantId,
        Long userId,
        PrometeoAccountValidationRequest request
    ) {
        try {
            PrometeoAccountValidationResponse response = client.validateAccount(request);
            meterRegistry.counter(
                "micuota_prometeo_account_validation_requests_total",
                "outcome", response.success() ? "success" : "provider_error"
            ).increment();
            return response;
        } catch (RuntimeException exception) {
            meterRegistry.counter(
                "micuota_prometeo_account_validation_requests_total",
                "outcome", "technical_error"
            ).increment();
            log.warn(
                "Prometeo validation failed tenantId={} userId={} countryCode={} reason={}",
                tenantId,
                userId,
                request.countryCode(),
                exception.getMessage()
            );
            throw exception;
        }
    }
}
