package com.micuota.mvp.service;

import com.micuota.mvp.domain.BankAccountVerificationStatus;
import java.time.OffsetDateTime;

public record StudentBankAccountView(
    Long id,
    Long studentUserId,
    String studentName,
    String studentEmail,
    String provider,
    String countryCode,
    String accountType,
    String bankCode,
    String branchCode,
    String accountHolderName,
    String accountNumberMasked,
    String accountLast4,
    BankAccountVerificationStatus verificationStatus,
    Integer providerCode,
    String providerMessage,
    boolean preferred,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime verifiedAt
) {
}
