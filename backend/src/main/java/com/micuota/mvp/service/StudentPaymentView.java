package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import java.math.BigDecimal;

public record StudentPaymentView(
    Long operationId,
    String description,
    String courseName,
    BigDecimal amount,
    String currency,
    PaymentFlowType flowType,
    OperationStatus status,
    String checkoutUrl,
    String professionalName
) {
}
