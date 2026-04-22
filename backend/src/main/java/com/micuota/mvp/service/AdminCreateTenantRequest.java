package com.micuota.mvp.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminCreateTenantRequest(
    @NotBlank String tenantName,
    String tenantSlug,
    @NotBlank String adminFullName,
    @NotBlank @Email String adminEmail,
    @NotBlank String adminPassword
) {
}
