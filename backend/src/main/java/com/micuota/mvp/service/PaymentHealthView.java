package com.micuota.mvp.service;

public record PaymentHealthView(
    long totalPayments,
    long successPayments,
    long pendingPayments,
    long failedPayments,
    long overduePayments,
    long recoveringPayments,
    long reconciliationMismatches,
    double successRate,
    String recommendation
) {
}
