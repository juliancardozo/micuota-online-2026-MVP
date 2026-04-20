package com.micuota.mvp.service;

import com.micuota.mvp.domain.Course;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.SessionActivity;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.SessionActivityRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentKpiService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final SessionActivityRepository sessionActivityRepository;
    private final CourseRepository courseRepository;

    public PaymentKpiService(
        TeacherProfileRepository teacherProfileRepository,
        PaymentOperationRepository paymentOperationRepository,
        SessionActivityRepository sessionActivityRepository,
        CourseRepository courseRepository
    ) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.sessionActivityRepository = sessionActivityRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public PaymentKpiFrameworkView buildTenantKpis(Long tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);
        YearMonth currentMonth = YearMonth.from(now);

        List<TeacherProfile> professionals = teacherProfileRepository.findByUserTenantId(tenantId);
        Set<Long> teacherProfileIds = professionals.stream().map(TeacherProfile::getId).collect(Collectors.toSet());
        Set<Long> professionalUserIds = professionals.stream().map(profile -> profile.getUser().getId()).collect(Collectors.toSet());

        List<PaymentOperation> payments = teacherProfileIds.isEmpty()
            ? List.of()
            : paymentOperationRepository.findByTeacherIdInOrderByCreatedAtDesc(teacherProfileIds.stream().toList());

        List<SessionActivity> sessions = sessionActivityRepository.findAll().stream()
            .filter(session -> tenantId.equals(session.getTenantId()))
            .filter(session -> professionalUserIds.contains(session.getUserId()))
            .toList();

        Map<Long, List<PaymentOperation>> paymentsByProfessional = payments.stream()
            .collect(Collectors.groupingBy(PaymentOperation::getTeacherId));
        Map<Long, Long> profileIdByUserId = professionals.stream()
            .collect(Collectors.toMap(profile -> profile.getUser().getId(), TeacherProfile::getId));
        Map<Long, List<SessionActivity>> sessionsByProfile = sessions.stream()
            .filter(session -> profileIdByUserId.containsKey(session.getUserId()))
            .collect(Collectors.groupingBy(session -> profileIdByUserId.get(session.getUserId())));

        PaymentKpiFrameworkView.ActivationKpis activation = buildActivation(professionals, paymentsByProfessional, sessionsByProfile);
        PaymentKpiFrameworkView.ConversionKpis conversion = buildConversion(payments, now);
        PaymentKpiFrameworkView.RetentionKpis retention = buildRetention(professionals, paymentsByProfessional, thirtyDaysAgo);
        PaymentKpiFrameworkView.RevenueKpis revenue = buildRevenue(tenantId, payments, currentMonth);
        PaymentKpiFrameworkView.RiskKpis risk = buildRisk(payments);

        return new PaymentKpiFrameworkView(activation, conversion, retention, revenue, risk);
    }

    private PaymentKpiFrameworkView.ActivationKpis buildActivation(
        List<TeacherProfile> professionals,
        Map<Long, List<PaymentOperation>> paymentsByProfessional,
        Map<Long, List<SessionActivity>> sessionsByProfile
    ) {
        long totalProfessionals = professionals.size();
        long professionalsWithFirstPayment = 0;
        long under24h = 0;
        double totalHoursToFirstSuccess = 0D;
        int firstSuccessSamples = 0;

        for (TeacherProfile profile : professionals) {
            List<PaymentOperation> operations = paymentsByProfessional.getOrDefault(profile.getId(), List.of());
            if (!operations.isEmpty()) {
                professionalsWithFirstPayment++;
            }

            OffsetDateTime firstPaymentAt = operations.stream()
                .map(PaymentOperation::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

            OffsetDateTime firstSuccessAt = operations.stream()
                .filter(payment -> payment.getStatus() == OperationStatus.SUCCESS)
                .map(PaymentOperation::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

            OffsetDateTime firstSessionAt = sessionsByProfile.getOrDefault(profile.getId(), List.of()).stream()
                .map(SessionActivity::getStartedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

            if (firstPaymentAt != null && firstSessionAt != null && !firstPaymentAt.isAfter(firstSessionAt.plusHours(24))) {
                under24h++;
            }

            if (firstSuccessAt != null && firstSessionAt != null) {
                long minutes = ChronoUnit.MINUTES.between(firstSessionAt, firstSuccessAt);
                if (minutes >= 0) {
                    totalHoursToFirstSuccess += minutes / 60.0;
                    firstSuccessSamples++;
                }
            }
        }

        double under24Percent = percentage(under24h, totalProfessionals);
        double avgHoursToFirstSuccess = firstSuccessSamples == 0 ? 0D : round(totalHoursToFirstSuccess / firstSuccessSamples);

        return new PaymentKpiFrameworkView.ActivationKpis(
            totalProfessionals,
            professionalsWithFirstPayment,
            under24Percent,
            avgHoursToFirstSuccess
        );
    }

    private PaymentKpiFrameworkView.ConversionKpis buildConversion(List<PaymentOperation> payments, OffsetDateTime now) {
        long totalAttempts = payments.size();
        long totalSuccess = payments.stream().filter(payment -> payment.getStatus() == OperationStatus.SUCCESS).count();
        long abandoned = payments.stream()
            .filter(payment -> payment.getStatus() == OperationStatus.CREATED || payment.getStatus() == OperationStatus.PENDING)
            .filter(payment -> payment.getCreatedAt() != null && payment.getCreatedAt().isBefore(now.minusHours(24)))
            .count();

        Map<String, Double> byMethod = new LinkedHashMap<>();
        for (PaymentProviderType provider : PaymentProviderType.values()) {
            long providerAttempts = payments.stream().filter(payment -> payment.getProvider() == provider).count();
            if (providerAttempts == 0) {
                continue;
            }
            long providerSuccess = payments.stream()
                .filter(payment -> payment.getProvider() == provider && payment.getStatus() == OperationStatus.SUCCESS)
                .count();
            byMethod.put(provider.name(), percentage(providerSuccess, providerAttempts));
        }

        return new PaymentKpiFrameworkView.ConversionKpis(
            percentage(totalSuccess, totalAttempts),
            byMethod,
            percentage(abandoned, totalAttempts)
        );
    }

    private PaymentKpiFrameworkView.RetentionKpis buildRetention(
        List<TeacherProfile> professionals,
        Map<Long, List<PaymentOperation>> paymentsByProfessional,
        OffsetDateTime since
    ) {
        long recurringBaseline = 0;
        long recurringChurn = 0;
        long nonRecurringBaseline = 0;
        long nonRecurringChurn = 0;
        long recurringProfessionals = 0;
        long withMonthlyRenewal = 0;

        for (TeacherProfile profile : professionals) {
            List<PaymentOperation> operations = paymentsByProfessional.getOrDefault(profile.getId(), List.of());
            boolean hasAnySuccess = operations.stream().anyMatch(payment -> payment.getStatus() == OperationStatus.SUCCESS);
            if (!hasAnySuccess) {
                continue;
            }

            boolean hasRecurring = operations.stream().anyMatch(payment -> payment.getFlowType() == PaymentFlowType.SUBSCRIPTION);
            boolean activeLast30 = operations.stream()
                .anyMatch(payment -> payment.getStatus() == OperationStatus.SUCCESS && payment.getCreatedAt() != null && !payment.getCreatedAt().isBefore(since));

            if (hasRecurring) {
                recurringBaseline++;
                if (!activeLast30) {
                    recurringChurn++;
                }
                recurringProfessionals++;
                boolean hasMonthlyRenewal = operations.stream().anyMatch(payment ->
                    payment.getFlowType() == PaymentFlowType.SUBSCRIPTION
                        && payment.getStatus() == OperationStatus.SUCCESS
                        && payment.getCreatedAt() != null
                        && !payment.getCreatedAt().isBefore(since)
                );
                if (hasMonthlyRenewal) {
                    withMonthlyRenewal++;
                }
            } else {
                nonRecurringBaseline++;
                if (!activeLast30) {
                    nonRecurringChurn++;
                }
            }
        }

        return new PaymentKpiFrameworkView.RetentionKpis(
            percentage(recurringChurn, recurringBaseline),
            percentage(nonRecurringChurn, nonRecurringBaseline),
            percentage(withMonthlyRenewal, recurringProfessionals)
        );
    }

    private PaymentKpiFrameworkView.RevenueKpis buildRevenue(Long tenantId, List<PaymentOperation> payments, YearMonth currentMonth) {
        List<PaymentOperation> currentMonthSuccess = payments.stream()
            .filter(payment -> payment.getStatus() == OperationStatus.SUCCESS)
            .filter(payment -> payment.getCreatedAt() != null && YearMonth.from(payment.getCreatedAt()).equals(currentMonth))
            .toList();

        BigDecimal gross = currentMonthSuccess.stream()
            .map(PaymentOperation::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal processingFees = currentMonthSuccess.stream()
            .map(payment -> payment.getProcessingFeeAmount() == null ? BigDecimal.ZERO : payment.getProcessingFeeAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal advancedFees = currentMonthSuccess.stream()
            .map(payment -> payment.getAdvancedFeatureFeeAmount() == null ? BigDecimal.ZERO : payment.getAdvancedFeatureFeeAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPlatformRevenue = processingFees.add(advancedFees);
        double takeRate = gross.compareTo(BigDecimal.ZERO) == 0
            ? 0D
            : round(totalPlatformRevenue.multiply(BigDecimal.valueOf(100)).divide(gross, 4, RoundingMode.HALF_UP).doubleValue());

        Map<Long, Course> coursesById = courseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .collect(Collectors.toMap(Course::getId, course -> course, (left, right) -> left));

        Map<String, BigDecimal> revenueByVertical = new HashMap<>();
        Map<String, Set<Long>> professionalsByVertical = new HashMap<>();

        for (PaymentOperation payment : currentMonthSuccess) {
            String vertical = resolveVertical(coursesById.get(payment.getCourseId()));
            revenueByVertical.put(vertical, revenueByVertical.getOrDefault(vertical, BigDecimal.ZERO).add(payment.getAmount()));
            professionalsByVertical.computeIfAbsent(vertical, ignored -> new java.util.HashSet<>()).add(payment.getTeacherId());
        }

        Map<String, Double> arpaByVertical = new LinkedHashMap<>();
        revenueByVertical.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .forEach(entry -> {
                int professionals = professionalsByVertical.getOrDefault(entry.getKey(), Set.of()).size();
                double arpa = professionals == 0 ? 0D : entry.getValue().divide(BigDecimal.valueOf(professionals), 2, RoundingMode.HALF_UP).doubleValue();
                arpaByVertical.put(entry.getKey(), arpa);
            });

        return new PaymentKpiFrameworkView.RevenueKpis(
            gross.setScale(2, RoundingMode.HALF_UP).doubleValue(),
            takeRate,
            arpaByVertical
        );
    }

    private PaymentKpiFrameworkView.RiskKpis buildRisk(List<PaymentOperation> payments) {
        long successes = payments.stream().filter(payment -> payment.getStatus() == OperationStatus.SUCCESS).count();
        long disputes = payments.stream()
            .filter(payment -> payment.getFailureReason() != null)
            .filter(payment -> {
                String reason = payment.getFailureReason().toUpperCase(Locale.ROOT);
                return reason.contains("DISPUTE") || reason.contains("CHARGEBACK");
            })
            .count();

        List<PaymentOperation> failures = payments.stream()
            .filter(payment -> payment.getStatus() == OperationStatus.FAILURE)
            .toList();
        Map<String, Long> failuresByReason = failures.stream()
            .collect(Collectors.groupingBy(payment -> {
                if (payment.getFailureReason() == null || payment.getFailureReason().isBlank()) {
                    return "UNKNOWN";
                }
                return payment.getFailureReason().toUpperCase(Locale.ROOT);
            }, Collectors.counting()));

        Map<String, Double> failureRateByReason = new LinkedHashMap<>();
        failuresByReason.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .forEach(entry -> failureRateByReason.put(entry.getKey(), percentage(entry.getValue(), failures.size())));

        return new PaymentKpiFrameworkView.RiskKpis(
            percentage(disputes, successes),
            failureRateByReason
        );
    }

    private String resolveVertical(Course course) {
        if (course == null) {
            return "general";
        }
        String sample = (course.getName() + " " + course.getDescription()).toLowerCase(Locale.ROOT);
        if (sample.contains("psico") || sample.contains("terapia") || sample.contains("paciente")) {
            return "psicologia";
        }
        if (sample.contains("guitarra") || sample.contains("musica") || sample.contains("clase") || sample.contains("curso")) {
            return "educacion";
        }
        if (sample.contains("gym") || sample.contains("entren") || sample.contains("fitness")) {
            return "wellness";
        }
        return "general";
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return round(((double) numerator / (double) denominator) * 100D);
    }

    private double percentage(Number numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return round((numerator.doubleValue() / (double) denominator) * 100D);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
