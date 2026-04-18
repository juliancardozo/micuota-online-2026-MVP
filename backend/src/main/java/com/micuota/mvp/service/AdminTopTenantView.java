package com.micuota.mvp.service;

public record AdminTopTenantView(
    Long tenantId,
    String tenantSlug,
    String tenantName,
    long totalRevenueSuccess,
    long successfulPayments,
    long totalCourses
) {
}
