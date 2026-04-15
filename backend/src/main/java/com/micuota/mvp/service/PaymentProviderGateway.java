package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;

public interface PaymentProviderGateway {

    PaymentProviderType provider();

    ProviderCheckoutResult createCheckout(
        PaymentFlowType flowType,
        CreatePaymentRequest request,
        TeacherProviderCredentials credentials,
        String callbackBaseUrl
    );
}
