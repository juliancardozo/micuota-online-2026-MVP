package com.micuota.mvp.service;

public record EnrollmentView(
    Long id,
    Long courseId,
    String courseName,
    Long studentUserId,
    String studentName,
    String teacherName
) {
}
