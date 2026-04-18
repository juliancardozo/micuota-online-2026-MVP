package com.micuota.mvp.integration.prometeo.dto;

import jakarta.validation.constraints.NotBlank;

public record PrometeoAccountValidationRequest(
    @NotBlank(message = "countryCode es obligatorio")
    String countryCode,
    @NotBlank(message = "accountNumber es obligatorio")
    String accountNumber,
    @NotBlank(message = "accountType es obligatorio")
    String accountType,
    String bankCode,
    String branchCode,
    String documentNumber,
    String documentType
) {
}
