package com.micuota.mvp.service;

public record ProviderCheckoutResult(
    String providerReference,
    String checkoutUrl,
    String rawResponse
) {
}
