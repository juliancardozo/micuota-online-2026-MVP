package com.micuota.mvp.service;

import java.util.List;

public record ProfessorDashboardView(
    String professionalName,
    List<CourseView> courses,
    List<ProfessorCoursePaymentSummary> courseSummaries,
    List<StudentPaymentView> recentPayments
) {
}
