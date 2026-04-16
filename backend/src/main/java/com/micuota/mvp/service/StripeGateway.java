package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StripeGateway implements PaymentProviderGateway {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.STRIPE;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String ref = "ST-" + UUID.randomUUID().toString().substring(0, 8);
        String checkoutUrl = callbackBaseUrl + "/sandbox/stripe/checkout/" + ref;
        String raw = "{\"provider\":\"stripe\",\"flow\":\""
            + flowType
            + "\",\"mode\":\"payment_link\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }
}
