package com.micuota.mvp.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveStudentBankAccountRequest(
    @NotNull(message = "studentUserId es obligatorio")
    Long studentUserId,
    @NotBlank(message = "countryCode es obligatorio")
    String countryCode,
    @NotBlank(message = "accountNumber es obligatorio")
    String accountNumber,
    @NotBlank(message = "accountType es obligatorio")
    String accountType,
    String bankCode,
    String branchCode,
    String documentNumber,
    String documentType,
    String accountHolderName,
    Boolean preferred
) {
}
