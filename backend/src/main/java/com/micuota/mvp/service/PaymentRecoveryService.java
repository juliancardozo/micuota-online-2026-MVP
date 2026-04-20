package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryService.class);

    private final PaymentOperationRepository paymentOperationRepository;
    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentNotificationService paymentNotificationService;

    public PaymentRecoveryService(
        PaymentOperationRepository paymentOperationRepository,
        UserRepository userRepository,
        TeacherProfileRepository teacherProfileRepository,
        PaymentNotificationService paymentNotificationService
    ) {
        this.paymentOperationRepository = paymentOperationRepository;
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentNotificationService = paymentNotificationService;
    }

    @Scheduled(cron = "${app.payments.dunning.cron:0 */30 * * * *}")
    @Transactional
    public void runDunningCycle() {
        OffsetDateTime now = OffsetDateTime.now();

        List<PaymentOperation> retryCandidates = paymentOperationRepository
            .findTop300ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                List.of(OperationStatus.FAILURE, OperationStatus.CREATED, OperationStatus.PENDING),
                now
            );
        List<PaymentOperation> staleCandidates = paymentOperationRepository
            .findTop300ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(OperationStatus.CREATED, OperationStatus.PENDING),
                now.minus(24, ChronoUnit.HOURS)
            );
        List<PaymentOperation> overdueCandidates = paymentOperationRepository
            .findTop300ByStatusNotAndDueAtBeforeOrderByDueAtAsc(OperationStatus.SUCCESS, now);

        Map<Long, PaymentOperation> uniqueCandidates = new LinkedHashMap<>();
        retryCandidates.forEach(operation -> uniqueCandidates.put(operation.getId(), operation));
        staleCandidates.forEach(operation -> uniqueCandidates.put(operation.getId(), operation));

        List<PaymentOperation> dirty = new ArrayList<>();

        for (PaymentOperation operation : uniqueCandidates.values()) {
            if (applyDunningAttempt(operation, now)) {
                dirty.add(operation);
            }
        }

        for (PaymentOperation operation : overdueCandidates) {
            if (applyOverdueRecovery(operation, now)) {
                dirty.add(operation);
            }
        }

        if (!dirty.isEmpty()) {
            paymentOperationRepository.saveAll(dirty);
            log.info("Dunning cycle processed operations={}", dirty.size());
        }
    }

    @Scheduled(cron = "${app.payments.reconciliation.cron:0 5 2 * * *}")
    @Transactional
    public void runDailyReconciliation() {
        OffsetDateTime now = OffsetDateTime.now();
        List<PaymentOperation> operations = paymentOperationRepository.findAll();
        int mismatches = 0;
        String sample = null;

        for (PaymentOperation operation : operations) {
            boolean mismatch = isMismatch(operation, now);
            if (mismatch) {
                mismatches++;
                operation.setReconciliationStatus("MISMATCH");
                if (sample == null) {
                    sample = "operationId=" + operation.getId() + " status=" + operation.getStatus();
                }
            } else if (operation.getStatus() == OperationStatus.SUCCESS) {
                operation.setReconciliationStatus("MATCHED");
            } else {
                operation.setReconciliationStatus("PENDING");
            }
            operation.setLastReconciledAt(now);
        }

        paymentOperationRepository.saveAll(operations);

        if (mismatches > 0) {
            paymentNotificationService.sendOpsReconciliationAlert(mismatches, sample == null ? "sin muestra" : sample);
            log.warn("Daily reconciliation mismatches={}", mismatches);
        } else {
            log.info("Daily reconciliation completed with no mismatches");
        }
    }

    private boolean applyDunningAttempt(PaymentOperation operation, OffsetDateTime now) {
        if (operation.getStatus() == OperationStatus.SUCCESS) {
            return false;
        }

        int attempts = operation.getRetryCount() == null ? 0 : operation.getRetryCount();
        int nextAttempt = attempts + 1;
        String failureReason = normalizeFailureReason(operation.getFailureReason(), operation.getStatus());

        String payerEmail = resolvePayerEmail(operation.getStudentUserId());
        String teacherName = resolveTeacherName(operation.getTeacherId());

        if (payerEmail != null && shouldSendReminder(operation.getLastReminderAt(), now)) {
            String subject = "MiCuota | Recordatorio para completar tu pago";
            String message = messageByFailureReason(failureReason, operation.getStatus(), nextAttempt);
            paymentNotificationService.sendDunningReminderEmail(payerEmail, teacherName, operation, subject, message);
            operation.setLastReminderAt(now);
        }

        operation.setRetryCount(nextAttempt);
        operation.setFailureReason(failureReason);
        operation.setNextRetryAt(calculateNextRetry(nextAttempt, now));
        operation.setUpdatedAt(now);
        operation.setReconciliationStatus("PENDING");

        return true;
    }

    private boolean applyOverdueRecovery(PaymentOperation operation, OffsetDateTime now) {
        if (operation.getStatus() == OperationStatus.SUCCESS) {
            return false;
        }

        if (!shouldSendReminder(operation.getLastReminderAt(), now)) {
            return false;
        }

        String payerEmail = resolvePayerEmail(operation.getStudentUserId());
        String teacherName = resolveTeacherName(operation.getTeacherId());
        if (payerEmail != null) {
            paymentNotificationService.sendDunningReminderEmail(
                payerEmail,
                teacherName,
                operation,
                "MiCuota | Tienes un pago vencido para retomar",
                "Tu pago ya figura como vencido. Puedes retomarlo ahora desde el mismo link para mantener tu servicio activo."
            );
        }

        int attempts = operation.getRetryCount() == null ? 0 : operation.getRetryCount();
        operation.setRetryCount(attempts + 1);
        operation.setFailureReason("OVERDUE_PAYMENT");
        operation.setLastReminderAt(now);
        operation.setNextRetryAt(now.plus(24, ChronoUnit.HOURS));
        operation.setUpdatedAt(now);
        operation.setReconciliationStatus("PENDING");
        return true;
    }

    private String resolvePayerEmail(Long studentUserId) {
        if (studentUserId == null) {
            return null;
        }
        return userRepository.findById(studentUserId)
            .map(user -> user.getEmail() == null ? null : user.getEmail().trim().toLowerCase(Locale.ROOT))
            .orElse(null);
    }

    private String resolveTeacherName(Long teacherId) {
        if (teacherId == null) {
            return "Tu profesional";
        }
        return teacherProfileRepository.findById(teacherId)
            .map(profile -> profile.getDisplayName() == null || profile.getDisplayName().isBlank() ? "Tu profesional" : profile.getDisplayName())
            .orElse("Tu profesional");
    }

    private String normalizeFailureReason(String reason, OperationStatus status) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        if (status == OperationStatus.FAILURE) {
            return "PROCESSOR_DECLINED";
        }
        if (status == OperationStatus.PENDING) {
            return "PROCESSOR_REVIEW";
        }
        return "CHECKOUT_ABANDONED";
    }

    private String messageByFailureReason(String reason, OperationStatus status, int attempt) {
        String prefix = "Intento " + attempt + ": ";
        if ("PROCESSOR_DECLINED".equalsIgnoreCase(reason)) {
            return prefix + "el medio de pago fue rechazado. Prueba de nuevo o usa otro metodo para completar el cobro.";
        }
        if ("PROCESSOR_REVIEW".equalsIgnoreCase(reason) || status == OperationStatus.PENDING) {
            return prefix + "tu pago sigue en revision. Si no se acredita, puedes retomarlo desde este mismo link.";
        }
        if ("OVERDUE_PAYMENT".equalsIgnoreCase(reason)) {
            return prefix + "tu cuota esta vencida. Retomala ahora para evitar interrupciones del servicio.";
        }
        return prefix + "tu pago sigue pendiente. Retomalo desde este link embebido en MiCuota.";
    }

    private OffsetDateTime calculateNextRetry(int retryCount, OffsetDateTime now) {
        if (retryCount <= 1) {
            return now.plus(30, ChronoUnit.MINUTES);
        }
        if (retryCount == 2) {
            return now.plus(6, ChronoUnit.HOURS);
        }
        return now.plus(24, ChronoUnit.HOURS);
    }

    private boolean shouldSendReminder(OffsetDateTime lastReminderAt, OffsetDateTime now) {
        return lastReminderAt == null || lastReminderAt.isBefore(now.minus(6, ChronoUnit.HOURS));
    }

    private boolean isMismatch(PaymentOperation operation, OffsetDateTime now) {
        if (operation.getStatus() == OperationStatus.SUCCESS) {
            return isBlank(operation.getProviderReference()) || isBlank(operation.getCheckoutUrl());
        }

        if ((operation.getStatus() == OperationStatus.CREATED || operation.getStatus() == OperationStatus.PENDING)
            && operation.getCreatedAt() != null
            && operation.getCreatedAt().isBefore(now.minus(48, ChronoUnit.HOURS))) {
            return true;
        }

        return operation.getStatus() == OperationStatus.FAILURE
            && operation.getRetryCount() != null
            && operation.getRetryCount() >= 3
            && (operation.getLastReminderAt() == null || operation.getLastReminderAt().isBefore(now.minus(7, ChronoUnit.DAYS)));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
