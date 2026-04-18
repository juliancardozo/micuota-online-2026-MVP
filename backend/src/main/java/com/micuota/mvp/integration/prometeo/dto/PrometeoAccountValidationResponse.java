package com.micuota.mvp.integration.prometeo.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PrometeoAccountValidationResponse(
    String provider,
    boolean sandbox,
    boolean success,
    int upstreamHttpStatus,
    Integer providerCode,
    String message,
    String countryCode,
    String accountNumberMasked,
    JsonNode data,
    JsonNode validationData
) {
}
