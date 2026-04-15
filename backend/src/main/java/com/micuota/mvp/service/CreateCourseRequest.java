package com.micuota.mvp.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCourseRequest(
    @NotBlank String name,
    @NotBlank String description,
    @NotNull Long teacherUserId
) {
}
