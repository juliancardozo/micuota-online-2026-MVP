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
        String successUrl = callbackBaseUrl + "/api/callbacks/success/by-reference?providerReference=" + ref;
        String pendingUrl = callbackBaseUrl + "/api/callbacks/pending/by-reference?providerReference=" + ref;
        String failureUrl = callbackBaseUrl + "/api/callbacks/failed/by-reference?providerReference=" + ref;

        String raw = "{\"provider\":\"mercadopago\",\"flow\":\""
            + flowType
            + "\",\"back_urls\":{\"success\":\""
            + successUrl + "\",\"pending\":\""
            + pendingUrl + "\",\"failure\":\""
            + failureUrl + "\"}}";

        return new ProviderCheckoutResult(ref, checkoutUrl, raw);
    }
}
