package com.micuota.mvp.service;

public record CourseView(
    Long id,
    String name,
    String description,
    Long teacherUserId,
    String teacherName
) {
}
