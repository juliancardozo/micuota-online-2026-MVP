package com.micuota.mvp.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateTenantBillingSettingsRequest(
    @NotBlank String planCode,
    @Min(0) @Max(5000) Integer takeRateBps,
    @Min(0) @Max(2500) Integer advancedDunningFeeBps,
    Boolean recoveryAutomationEnabled,
    Boolean advancedAnalyticsEnabled,
    Boolean integrationsEnabled
) {
}
