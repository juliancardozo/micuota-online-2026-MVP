package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WooCommerceGateway implements PaymentProviderGateway {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.WOOCOMMERCE;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String apiKey = credentials.wooCommerceApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("El profesor no tiene API key de WooCommerce configurada");
        }

        String ref = "WC-" + UUID.randomUUID().toString().substring(0, 8);
        String checkoutUrl = callbackBaseUrl + "/sandbox/woocommerce/checkout/" + ref;
        String raw = "{\"provider\":\"woocommerce\",\"flow\":\"" + flowType + "\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }
}
