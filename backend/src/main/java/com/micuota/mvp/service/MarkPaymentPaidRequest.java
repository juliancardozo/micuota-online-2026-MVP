package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaidMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkPaymentPaidRequest(
    @NotNull PaidMethod method,
    @Size(max = 300) String note
) {
}
