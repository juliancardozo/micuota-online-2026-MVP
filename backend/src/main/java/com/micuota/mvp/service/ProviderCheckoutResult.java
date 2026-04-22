package com.micuota.mvp.service;

public record ProviderCheckoutResult(
    String providerReference,
    String externalReference,
    String checkoutUrl,
    String rawResponse
) {
    public ProviderCheckoutResult(String providerReference, String checkoutUrl, String rawResponse) {
        this(providerReference, null, checkoutUrl, rawResponse);
    }
}
