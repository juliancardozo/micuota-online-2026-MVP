package com.micuota.mvp.service;

import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(
    @NotNull Long courseId,
    @NotNull Long studentUserId
) {
}
