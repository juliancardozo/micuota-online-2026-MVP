package com.micuota.mvp.service;

public record UpdatePaymentSettingsRequest(
    String transferAlias,
    String transferBankName
) {
}
