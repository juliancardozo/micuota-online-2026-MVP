package com.micuota.mvp.service;

public record AdminTenantProvisioningResult(
    Long tenantId,
    String tenantSlug,
    Long tenantAdminUserId,
    String tenantAdminEmail,
    String setupUrl
) {
}
