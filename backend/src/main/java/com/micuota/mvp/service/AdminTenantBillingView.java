package com.micuota.mvp.service;

import java.math.BigDecimal;

public record AdminTenantBillingView(
    Long tenantId,
    String tenantName,
    String tenantSlug,
    String planCode,
    int takeRateBps,
    int advancedDunningFeeBps,
    boolean recoveryAutomationEnabled,
    long professionals,
    long professionalsConnectedViaOAuth,
    long paymentsWithCommission,
    BigDecimal commissionCollected
) {
}
