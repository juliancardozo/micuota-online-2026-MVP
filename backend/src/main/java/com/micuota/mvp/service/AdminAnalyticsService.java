package com.micuota.mvp.service;

import com.micuota.mvp.config.PlatformAdminSeeder;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaidMethod;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.LeadRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);
    private static final int RECENT_ACCOUNTS = 8;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final LeadRepository leadRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final EntityManager entityManager;

    public AdminAnalyticsService(
        TenantRepository tenantRepository,
        UserRepository userRepository,
        LeadRepository leadRepository,
        PaymentOperationRepository paymentOperationRepository,
        TeacherProfileRepository teacherProfileRepository,
        EntityManager entityManager
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.leadRepository = leadRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AdminOverviewView buildOverview() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Tenant> tenants = tenantRepository.findAll().stream()
            .filter(tenant -> !PlatformAdminSeeder.PLATFORM_TENANT_SLUG.equals(tenant.getSlug()))
            .toList();
        Set<Long> tenantIds = tenants.stream().map(Tenant::getId).collect(Collectors.toSet());
        List<User> users = userRepository.findAll().stream()
            .filter(user -> user.getRole() != UserRole.ADMIN && tenantIds.contains(user.getTenant().getId()))
            .toList();

        // Perfiles de profesionales de cuentas reales (sin el del administrador ni el tenant de plataforma).
        Map<Long, TeacherProfile> profiles = teacherProfileRepository.findAll().stream()
            .filter(profile -> profile.getUser().getRole() == UserRole.TEACHER
                && tenantIds.contains(profile.getUser().getTenant().getId()))
            .collect(Collectors.toMap(TeacherProfile::getId, profile -> profile));
        List<PaymentOperation> payments = paymentOperationRepository.findAll().stream()
            .filter(payment -> profiles.containsKey(payment.getTeacherId()))
            .toList();

        long connectedViaOAuth = profiles.values().stream().filter(TeacherProfile::isMpOauthConnected).count();
        long manualToken = profiles.values().stream()
            .filter(profile -> !profile.isMpOauthConnected() && hasText(profile.getMpAccessToken()))
            .count();

        return new AdminOverviewView(
            now,
            tenants.size(),
            users.stream().filter(user -> user.getRole() == UserRole.TEACHER).count(),
            connectedViaOAuth,
            manualToken,
            users.stream().filter(user -> user.getRole() == UserRole.STUDENT).count(),
            payments.size(),
            payments.stream().filter(payment -> payment.getStatus() == OperationStatus.SUCCESS).count(),
            payments.stream().filter(payment -> payment.getStatus() == OperationStatus.SUCCESS
                && payment.getPaidMethod() != null && payment.getPaidMethod() != PaidMethod.MERCADOPAGO).count(),
            payments.stream().filter(payment -> payment.getStatus() == OperationStatus.CREATED || payment.getStatus() == OperationStatus.PENDING).count(),
            leadRepository.count(),
            buildCurrencyTotals(payments),
            buildMonths(tenants, payments, now),
            buildRecentAccounts(tenants, users, profiles)
        );
    }

    private List<AdminOverviewView.CurrencyTotal> buildCurrencyTotals(List<PaymentOperation> payments) {
        Map<String, List<PaymentOperation>> paidByCurrency = payments.stream()
            .filter(payment -> payment.getStatus() == OperationStatus.SUCCESS)
            .collect(Collectors.groupingBy(payment -> payment.getCurrency().toUpperCase(Locale.ROOT), TreeMap::new, Collectors.toList()));
        return paidByCurrency.entrySet().stream()
            .map(entry -> new AdminOverviewView.CurrencyTotal(
                entry.getKey(),
                entry.getValue().size(),
                sum(entry.getValue().stream().map(PaymentOperation::getAmount).toList()),
                // Si se cobro por fuera de Mercado Pago, la comision no llego a cobrarse.
                sum(entry.getValue().stream()
                    .filter(payment -> payment.getPaidMethod() == null || payment.getPaidMethod() == PaidMethod.MERCADOPAGO)
                    .map(PaymentOperation::getMarketplaceFeeAmount)
                    .toList())
            ))
            .toList();
    }

    private List<AdminOverviewView.MonthPoint> buildMonths(List<Tenant> tenants, List<PaymentOperation> payments, OffsetDateTime now) {
        Map<YearMonth, Long> accountsByMonth = new HashMap<>();
        for (Tenant tenant : tenants) {
            if (tenant.getCreatedAt() != null) {
                accountsByMonth.merge(YearMonth.from(tenant.getCreatedAt()), 1L, Long::sum);
            }
        }
        Map<YearMonth, Long> paidByMonth = new HashMap<>();
        for (PaymentOperation payment : payments) {
            if (payment.getStatus() == OperationStatus.SUCCESS) {
                OffsetDateTime when = payment.getUpdatedAt() != null ? payment.getUpdatedAt() : payment.getCreatedAt();
                paidByMonth.merge(YearMonth.from(when), 1L, Long::sum);
            }
        }
        List<AdminOverviewView.MonthPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.from(now).minusMonths(5);
        for (int i = 0; i < 6; i++) {
            points.add(new AdminOverviewView.MonthPoint(
                current.format(PERIOD_FORMAT),
                accountsByMonth.getOrDefault(current, 0L),
                paidByMonth.getOrDefault(current, 0L)
            ));
            current = current.plusMonths(1);
        }
        return points;
    }

    private List<AdminOverviewView.RecentAccount> buildRecentAccounts(
        List<Tenant> tenants,
        List<User> users,
        Map<Long, TeacherProfile> profiles
    ) {
        Map<Long, List<TeacherProfile>> profilesByTenant = profiles.values().stream()
            .collect(Collectors.groupingBy(profile -> profile.getUser().getTenant().getId()));
        return tenants.stream()
            .sorted(Comparator.comparing(Tenant::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(RECENT_ACCOUNTS)
            .map(tenant -> {
                User contact = users.stream()
                    .filter(user -> user.getTenant().getId().equals(tenant.getId()))
                    .filter(user -> user.getRole() == UserRole.TEACHER || user.getRole() == UserRole.TENANT_ADMIN)
                    .min(Comparator.comparing((User user) -> user.getRole() == UserRole.TEACHER ? 0 : 1).thenComparing(User::getId))
                    .orElse(null);
                return new AdminOverviewView.RecentAccount(
                    tenant.getId(),
                    tenant.getName(),
                    tenant.getSlug(),
                    tenant.getCreatedAt(),
                    contact == null ? null : contact.getFullName(),
                    contact == null ? null : contact.getEmail(),
                    mercadoPagoConnection(profilesByTenant.getOrDefault(tenant.getId(), List.of()))
                );
            })
            .toList();
    }

    static String mercadoPagoConnection(List<TeacherProfile> profiles) {
        if (profiles.stream().anyMatch(TeacherProfile::isMpOauthConnected)) {
            return "OAUTH";
        }
        return profiles.stream().anyMatch(profile -> hasText(profile.getMpAccessToken())) ? "MANUAL" : "NONE";
    }

    /**
     * Borra todas las cuentas salvo las indicadas, la de plataforma y la del administrador, con sus usuarios,
     * profesionales, cursos, cuentas bancarias, sesiones y cobros. Con deleteAllPayments borra tambien los
     * cobros de las cuentas conservadas. Los leads del CRM no se tocan.
     */
    @Transactional
    public AdminResetResult resetData(AdminResetRequest request) {
        List<String> requestedSlugs = request.keepTenantSlugs() == null ? List.of() : request.keepTenantSlugs();
        Set<Long> keptIds = new LinkedHashSet<>();
        for (String slug : requestedSlugs) {
            String normalized = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
            Tenant tenant = tenantRepository.findBySlug(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada: " + normalized));
            keptIds.add(tenant.getId());
        }
        tenantRepository.findBySlug(PlatformAdminSeeder.PLATFORM_TENANT_SLUG).ifPresent(tenant -> keptIds.add(tenant.getId()));
        userRepository.findAllByRole(UserRole.ADMIN).forEach(admin -> keptIds.add(admin.getTenant().getId()));
        if (keptIds.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos una cuenta para conservar");
        }

        // Cobros a borrar: todos, o los de profesionales de cuentas borradas / del administrador / sin perfil.
        String paymentsFilter = request.deleteAllPayments()
            ? "SELECT id FROM payment_operations"
            : """
                SELECT po.id FROM payment_operations po
                WHERE po.teacher_id NOT IN (
                    SELECT tp.id FROM teacher_profiles tp JOIN users u ON u.id = tp.user_id
                    WHERE u.tenant_id IN (:keptIds) AND u.role <> 'ADMIN'
                )
                """;
        nativeUpdate("DELETE FROM payment_events WHERE operation_id IN (" + paymentsFilter + ")", keptIds, !request.deleteAllPayments());
        int deletedPayments = nativeUpdate("DELETE FROM payment_operations WHERE id IN (" + paymentsFilter + ")", keptIds, !request.deleteAllPayments());

        nativeUpdate("DELETE FROM student_bank_accounts WHERE tenant_id NOT IN (:keptIds)", keptIds, true);
        nativeUpdate("DELETE FROM course_enrollments WHERE tenant_id NOT IN (:keptIds)", keptIds, true);
        nativeUpdate("DELETE FROM courses WHERE tenant_id NOT IN (:keptIds)", keptIds, true);
        nativeUpdate("""
            DELETE FROM teacher_profiles
            WHERE user_id IN (SELECT id FROM users WHERE tenant_id NOT IN (:keptIds) OR role = 'ADMIN')
            """, keptIds, true);
        nativeUpdate("DELETE FROM analytics.session_activity WHERE tenant_id NOT IN (:keptIds)", keptIds, true);
        int deletedUsers = nativeUpdate("DELETE FROM users WHERE tenant_id NOT IN (:keptIds)", keptIds, true);
        int deletedTenants = nativeUpdate("DELETE FROM tenants WHERE id NOT IN (:keptIds)", keptIds, true);
        entityManager.clear();

        List<String> keptSlugs = tenantRepository.findAllById(keptIds).stream()
            .map(Tenant::getSlug)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        return new AdminResetResult(deletedTenants, deletedUsers, deletedPayments, keptSlugs);
    }

    private int nativeUpdate(String sql, Set<Long> keptIds, boolean usesKeptIds) {
        var query = entityManager.createNativeQuery(sql);
        if (usesKeptIds) {
            query.setParameter("keptIds", keptIds);
        }
        return query.executeUpdate();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
