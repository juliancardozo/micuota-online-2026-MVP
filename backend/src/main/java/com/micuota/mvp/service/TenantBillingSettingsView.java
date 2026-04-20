package com.micuota.mvp.service;

public record TenantBillingSettingsView(
    String planCode,
    int takeRateBps,
    int advancedDunningFeeBps,
    boolean recoveryAutomationEnabled,
    boolean advancedAnalyticsEnabled,
    boolean integrationsEnabled
) {
}
