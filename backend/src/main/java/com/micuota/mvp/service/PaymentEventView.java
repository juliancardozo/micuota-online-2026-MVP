package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentEventType;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentProviderType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentEventView(
    Long id,
    Long operationId,
    Long teacherId,
    Long studentUserId,
    Long courseId,
    PaymentProviderType provider,
    PaymentFlowType flowType,
    PaymentEventType eventType,
    OperationStatus statusFrom,
    OperationStatus statusTo,
    BigDecimal amount,
    String currency,
    String providerReference,
    String rawPayload,
    OffsetDateTime createdAt
) {
}
