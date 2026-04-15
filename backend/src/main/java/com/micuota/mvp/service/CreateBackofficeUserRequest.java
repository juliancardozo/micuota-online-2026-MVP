package com.micuota.mvp.service;

import com.micuota.mvp.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBackofficeUserRequest(
    @NotBlank String fullName,
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotNull UserRole role
) {
}
