package com.micuota.mvp.service;

import java.util.List;

public record StudentDashboardView(
    String studentName,
    List<CourseView> enrolledCourses,
    List<StudentPaymentView> payments
) {
}
