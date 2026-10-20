package com.micuota.mvp.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminTenantBillingView(
    Long tenantId,
    String tenantName,
    String tenantSlug,
    OffsetDateTime createdAt,
    String contactName,
    String contactEmail,
    String mercadoPagoConnection,
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
