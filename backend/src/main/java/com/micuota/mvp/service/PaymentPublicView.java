package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaidMethod;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Lo que ve quien paga: el cobro y todas las formas de pago que acepta el profesional. */
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
    String transferBankName,
    String transferAccountNumber,
    String transferAccountHolder,
    String otherPaymentInstructions,
    PaidMethod paidMethod,
    OffsetDateTime paidAt
) {
}
