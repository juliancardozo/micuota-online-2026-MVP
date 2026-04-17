package com.micuota.mvp.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AssignLeadRequest(
    @NotBlank @Email String email,
    @NotBlank String segment,
    @Min(0) @Max(100) Integer score
) {
}
