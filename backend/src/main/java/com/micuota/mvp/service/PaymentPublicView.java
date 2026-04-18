package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.math.BigDecimal;

public record PaymentPublicView(
    Long operationId,
    String professionalName,
    String description,
    BigDecimal amount,
    String currency,
    PaymentFlowType flowType,
    PaymentProviderType provider,
    OperationStatus status,
    String checkoutUrl,
    String transferAlias,
    String transferBankName
) {
}
