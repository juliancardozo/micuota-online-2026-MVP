package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateBackofficePaymentRequest(
    @NotNull PaymentProviderType provider,
    @NotBlank String description,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String currency,
    @NotBlank @Email String payerEmail,
    Long studentUserId,
    Long courseId
) {
}
