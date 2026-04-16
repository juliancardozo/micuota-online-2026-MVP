package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlexoGateway implements PaymentProviderGateway {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.PLEXO;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String ref = "PX-" + UUID.randomUUID().toString().substring(0, 8);
        String checkoutUrl = callbackBaseUrl + "/sandbox/plexo/checkout/" + ref;
        String raw = "{\"provider\":\"plexo\",\"flow\":\""
            + flowType
            + "\",\"mode\":\"payment_link\"}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }
}
