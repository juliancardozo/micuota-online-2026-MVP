package com.micuota.mvp.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PublicLeadCaptureRequest(
    @NotBlank @Email String email,
    String phone,
    @NotBlank String fullName,
    String source
) {
}
