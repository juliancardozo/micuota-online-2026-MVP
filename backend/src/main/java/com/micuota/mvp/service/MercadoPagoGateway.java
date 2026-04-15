package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoGateway implements PaymentProviderGateway {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.MERCADOPAGO;
    }

    @Override
    public ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    ) {
        String token = credentials.mercadoPagoAccessToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("El profesor no tiene token de MercadoPago configurado");
        }

        String ref = "MP-" + UUID.randomUUID().toString().substring(0, 8);
        String checkoutUrl = callbackBaseUrl + "/sandbox/mercadopago/checkout/" + ref;

        String raw = "{\"provider\":\"mercadopago\",\"flow\":\""
            + flowType
            + "\",\"back_urls\":{\"success\":\""
            + callbackBaseUrl + "/api/callbacks/success\",\"pending\":\""
            + callbackBaseUrl + "/api/callbacks/pending\",\"failure\":\""
            + callbackBaseUrl + "/api/callbacks/failure\"}}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }
}
