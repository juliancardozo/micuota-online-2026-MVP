package com.micuota.mvp.service;

import com.micuota.mvp.domain.Lead;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.SessionActivity;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.repository.CourseEnrollmentRepository;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.LeadRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.SessionActivityRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final LeadRepository leadRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final SessionActivityRepository sessionActivityRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final EntityManager entityManager;

    public AdminAnalyticsService(
        TenantRepository tenantRepository,
        UserRepository userRepository,
        CourseRepository courseRepository,
        CourseEnrollmentRepository courseEnrollmentRepository,
        LeadRepository leadRepository,
        PaymentOperationRepository paymentOperationRepository,
        SessionActivityRepository sessionActivityRepository,
        TeacherProfileRepository teacherProfileRepository,
        EntityManager entityManager
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.leadRepository = leadRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.sessionActivityRepository = sessionActivityRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AdminSystemDashboardView buildSystemDashboard() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sessionWindow = now.minusDays(30);
        OffsetDateTime activeCutoff = now.minusMinutes(5);

        List<PaymentOperation> payments = paymentOperationRepository.findAll();
        List<Lead> leads = leadRepository.findAll();
        List<SessionActivity> sessions = sessionActivityRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(sessionWindow);

        long totalRevenueSuccess = payments.stream()
            .filter(payment -> payment.getStatus() == OperationStatus.SUCCESS)
            .map(PaymentOperation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .longValue();

        long totalRevenueAllStatuses = payments.stream()
            .map(PaymentOperation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .longValue();

        long avgSessionDurationMinutes = sessions.isEmpty()
            ? 0
            : Math.round(sessions.stream()
                .mapToLong(session -> {
                    long seconds = session.getDurationSeconds() == null ? 0L : session.getDurationSeconds();
                    if (seconds == 0L && session.getLastSeenAt() != null) {
                        seconds = session.getLastSeenAt().toEpochSecond() - session.getStartedAt().toEpochSecond();
                    }
                    return Math.max(0L, seconds);
                })
                .average()
                .orElse(0D) / 60.0);

        return new AdminSystemDashboardView(
            now,
            tenantRepository.count(),
            userRepository.count(),
            courseRepository.count(),
            courseEnrollmentRepository.count(),
            leadRepository.count(),
            totalRevenueSuccess,
            totalRevenueAllStatuses,
            sessionActivityRepository.countByLastSeenAtGreaterThanEqualAndEndedAtIsNull(activeCutoff),
            sessions.size(),
            avgSessionDurationMinutes,
            buildLeadStatusBreakdown(leads),
            buildLeadSourceBreakdown(leads),
            buildTrends(payments, sessions, leads, now),
            buildTopTenants(payments)
        );
    }

    private List<AdminLeadStatusView> buildLeadStatusBreakdown(List<Lead> leads) {
        return leads.stream()
            .collect(LinkedHashMap<String, Long>::new, (acc, lead) -> {
                String status = lead.getStatus() == null || lead.getStatus().isBlank() ? "UNKNOWN" : lead.getStatus();
                acc.put(status, acc.getOrDefault(status, 0L) + 1);
            }, Map::putAll)
            .entrySet().stream()
            .map(entry -> new AdminLeadStatusView(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(AdminLeadStatusView::total).reversed())
            .toList();
    }

    private List<AdminLeadSourceView> buildLeadSourceBreakdown(List<Lead> leads) {
        return leads.stream()
            .collect(LinkedHashMap<String, Long>::new, (acc, lead) -> {
                String source = lead.getSource() == null || lead.getSource().isBlank() ? "UNKNOWN" : lead.getSource();
                acc.put(source, acc.getOrDefault(source, 0L) + 1);
            }, Map::putAll)
            .entrySet().stream()
            .map(entry -> new AdminLeadSourceView(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(AdminLeadSourceView::total).reversed())
            .toList();
    }

    private List<AdminTrendPointView> buildTrends(
        List<PaymentOperation> payments,
        List<SessionActivity> sessions,
        List<Lead> leads,
        OffsetDateTime now
    ) {
        Map<YearMonth, Long> revenueByMonth = new HashMap<>();
        for (PaymentOperation payment : payments) {
            if (payment.getStatus() != OperationStatus.SUCCESS) {
                continue;
            }
            YearMonth month = YearMonth.from(payment.getCreatedAt());
            revenueByMonth.put(month, revenueByMonth.getOrDefault(month, 0L) + payment.getAmount().longValue());
        }

        Map<YearMonth, Long> sessionsByMonth = new HashMap<>();
        for (SessionActivity session : sessions) {
            YearMonth month = YearMonth.from(session.getStartedAt());
            sessionsByMonth.put(month, sessionsByMonth.getOrDefault(month, 0L) + 1);
        }

        Map<YearMonth, Long> leadsByMonth = new HashMap<>();
        for (Lead lead : leads) {
            YearMonth month = YearMonth.from(lead.getCreatedAt());
            leadsByMonth.put(month, leadsByMonth.getOrDefault(month, 0L) + 1);
        }

        List<AdminTrendPointView> points = new ArrayList<>();
        YearMonth current = YearMonth.from(now).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            points.add(new AdminTrendPointView(
                current.format(PERIOD_FORMAT),
                revenueByMonth.getOrDefault(current, 0L),
                sessionsByMonth.getOrDefault(current, 0L),
                leadsByMonth.getOrDefault(current, 0L)
            ));
            current = current.plusMonths(1);
        }
        return points;
    }

    private List<AdminTopTenantView> buildTopTenants(List<PaymentOperation> payments) {
        Map<Long, TeacherProfile> teacherProfiles = teacherProfileRepository.findAll().stream()
            .collect(LinkedHashMap::new, (acc, profile) -> acc.put(profile.getId(), profile), Map::putAll);
        Map<Long, Tenant> tenants = tenantRepository.findAll().stream()
            .collect(LinkedHashMap::new, (acc, tenant) -> acc.put(tenant.getId(), tenant), Map::putAll);
        Map<Long, Long> revenueByTenant = new HashMap<>();
        Map<Long, Long> successOpsByTenant = new HashMap<>();

        for (PaymentOperation payment : payments) {
            if (payment.getStatus() != OperationStatus.SUCCESS) {
                continue;
            }
            TeacherProfile profile = teacherProfiles.get(payment.getTeacherId());
            if (profile == null || profile.getUser() == null || profile.getUser().getTenant() == null) {
                continue;
            }
            Long tenantId = profile.getUser().getTenant().getId();
            revenueByTenant.put(tenantId, revenueByTenant.getOrDefault(tenantId, 0L) + payment.getAmount().longValue());
            successOpsByTenant.put(tenantId, successOpsByTenant.getOrDefault(tenantId, 0L) + 1);
        }

        return revenueByTenant.entrySet().stream()
            .map(entry -> {
                Tenant tenant = tenants.get(entry.getKey());
                return new AdminTopTenantView(
                    entry.getKey(),
                    tenant != null ? tenant.getSlug() : "n/a",
                    tenant != null ? tenant.getName() : "Tenant",
                    entry.getValue(),
                    successOpsByTenant.getOrDefault(entry.getKey(), 0L),
                    courseRepository.countByTenantId(entry.getKey())
                );
            })
            .sorted(Comparator.comparingLong(AdminTopTenantView::totalRevenueSuccess).reversed())
            .limit(10)
            .toList();
    }

    @Transactional
    public AdminTenantCleanupResult purgeTenantsExcept(String keptTenantSlug) {
        Tenant keptTenant = tenantRepository.findBySlug(keptTenantSlug)
            .orElseThrow(() -> new IllegalArgumentException("Tenant base no encontrado: " + keptTenantSlug));
        long deletedTenants = tenantRepository.findAll().stream()
            .filter(tenant -> !tenant.getId().equals(keptTenant.getId()))
            .count();

        if (deletedTenants == 0) {
            return new AdminTenantCleanupResult(0, 1);
        }

        entityManager.createNativeQuery("""
            DELETE FROM payment_events
            WHERE operation_id IN (
                SELECT po.id
                FROM payment_operations po
                JOIN teacher_profiles tp ON tp.id = po.teacher_id
                JOIN users u ON u.id = tp.user_id
                WHERE u.tenant_id <> :keptTenantId
            )
            """)
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();

        entityManager.createNativeQuery("""
            DELETE FROM payment_operations
            WHERE teacher_id IN (
                SELECT tp.id
                FROM teacher_profiles tp
                JOIN users u ON u.id = tp.user_id
                WHERE u.tenant_id <> :keptTenantId
            )
            """)
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM student_bank_accounts WHERE tenant_id <> :keptTenantId")
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM course_enrollments WHERE tenant_id <> :keptTenantId")
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM courses WHERE tenant_id <> :keptTenantId")
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();
        entityManager.createNativeQuery("""
            DELETE FROM teacher_profiles
            WHERE user_id IN (SELECT id FROM users WHERE tenant_id <> :keptTenantId)
            """)
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM analytics.session_activity WHERE tenant_id <> :keptTenantId")
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users WHERE tenant_id <> :keptTenantId")
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM tenants WHERE id <> :keptTenantId")
            .setParameter("keptTenantId", keptTenant.getId())
            .executeUpdate();

        return new AdminTenantCleanupResult(deletedTenants, 1);
    }
}
