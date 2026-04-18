package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PrometeoGateway implements PaymentProviderGateway {

    @Value("${app.prometeo.api-key:}")
    private String platformPrometeoApiKey;

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.PROMETEO;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String apiKey = credentials.prometeoApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = platformPrometeoApiKey;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No hay API key de Prometeo configurada");
        }

        String ref = "PR-" + UUID.randomUUID().toString().substring(0, 8);
        String checkoutUrl = callbackBaseUrl + "/sandbox/prometeo/checkout/" + ref;
        String raw = "{\"provider\":\"prometeo\",\"flow\":\"" + flowType + "\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }
}
