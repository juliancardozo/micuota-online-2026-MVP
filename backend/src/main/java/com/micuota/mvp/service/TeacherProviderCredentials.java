package com.micuota.mvp.service;

public record TeacherProviderCredentials(
    String mercadoPagoAccessToken,
    String prometeoApiKey,
    String wooCommerceApiKey
) {
}
