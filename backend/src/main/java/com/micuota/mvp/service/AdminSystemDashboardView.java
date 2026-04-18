package com.micuota.mvp.service;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminSystemDashboardView(
    OffsetDateTime generatedAt,
    long totalTenants,
    long totalUsers,
    long totalCourses,
    long totalEnrollments,
    long totalLeads,
    long totalRevenueSuccess,
    long totalRevenueAllStatuses,
    long activeSessions,
    long sessionsStartedLast30Days,
    long avgSessionDurationMinutes,
    List<AdminLeadStatusView> leadStatusBreakdown,
    List<AdminLeadSourceView> leadSourceBreakdown,
    List<AdminTrendPointView> trends,
    List<AdminTopTenantView> topTenants
) {
}
