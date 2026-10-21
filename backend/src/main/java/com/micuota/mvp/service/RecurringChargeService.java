package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.RecurringCharge;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.RecurringChargeRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobros mensuales sin Mercado Pago: se crea el primer cobro al dar de alta y despues, cada mes, uno nuevo
 * que le llega al cliente por email (y entra en los recordatorios). Con Mercado Pago se usa su suscripcion.
 */
@Service
public class RecurringChargeService {

    private static final Logger log = LoggerFactory.getLogger(RecurringChargeService.class);
    static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private static final LocalTime RUN_AT = LocalTime.of(9, 0);
    private static final int MAX_DAY = 28;

    private final RecurringChargeRepository recurringChargeRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentService paymentService;

    public RecurringChargeService(
        RecurringChargeRepository recurringChargeRepository,
        TeacherProfileRepository teacherProfileRepository,
        PaymentService paymentService
    ) {
        this.recurringChargeRepository = recurringChargeRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentService = paymentService;
    }

    /** Da de alta el cobro mensual y crea ya el cobro de este mes, que es lo que devuelve. */
    @Transactional
    public PaymentOperation createFromBackoffice(Long tenantId, Long userId, UserRole role, CreateBackofficePaymentRequest request) {
        CreateBackofficePaymentRequest firstCharge = new CreateBackofficePaymentRequest(
            PaymentProviderType.MANUAL,
            request.description(),
            request.amount(),
            request.currency(),
            request.payerEmail(),
            request.studentUserId(),
            request.courseId(),
            request.teacherUserId()
        );
        PaymentOperation first = paymentService.createOneTimeForBackoffice(tenantId, userId, role, firstCharge);

        LocalDate today = LocalDate.now(ZONE);
        RecurringCharge charge = new RecurringCharge();
        charge.setTeacherProfileId(first.getTeacherId());
        charge.setStudentUserId(request.studentUserId());
        charge.setPayerEmail(request.payerEmail().trim().toLowerCase(Locale.ROOT));
        charge.setDescription(request.description().trim());
        charge.setAmount(request.amount());
        charge.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        charge.setDayOfMonth(Math.min(today.getDayOfMonth(), MAX_DAY));
        charge.setActive(true);
        charge.setCreatedAt(OffsetDateTime.now(ZONE));
        charge.setNextRunAt(nextRunAfter(today, charge.getDayOfMonth()));
        charge.setLastOperationId(first.getId());
        charge = recurringChargeRepository.save(charge);

        return paymentService.linkToRecurringCharge(first.getId(), charge.getId());
    }

    @Transactional(readOnly = true)
    public List<RecurringChargeView> listForBackoffice(Long tenantId, Long userId, UserRole role) {
        List<Long> profileIds = paymentService.teacherProfileIdsForBackoffice(tenantId, userId, role);
        if (profileIds.isEmpty()) {
            return List.of();
        }
        return recurringChargeRepository.findByTeacherProfileIdInAndActiveTrueOrderByCreatedAtDesc(profileIds).stream()
            .map(RecurringChargeService::toView)
            .toList();
    }

    @Transactional
    public RecurringChargeView stopFromBackoffice(Long tenantId, Long userId, UserRole role, Long recurringChargeId) {
        RecurringCharge charge = recurringChargeRepository.findById(recurringChargeId)
            .filter(c -> paymentService.teacherProfileIdsForBackoffice(tenantId, userId, role).contains(c.getTeacherProfileId()))
            .orElseThrow(() -> new IllegalArgumentException("Cobro mensual no encontrado"));
        charge.setActive(false);
        charge.setStoppedAt(OffsetDateTime.now(ZONE));
        return toView(recurringChargeRepository.save(charge));
    }

    @Scheduled(cron = "${app.payments.recurring.cron:0 0 * * * *}")
    public void generateDueCharges() {
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        for (RecurringCharge charge : recurringChargeRepository.findTop200ByActiveTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now)) {
            try {
                generate(charge, now);
            } catch (RuntimeException exception) {
                // Se reintenta en una hora; un error de un cobro no frena a los demas.
                log.warn("No se pudo generar el cobro mensual recurringChargeId={}: {}", charge.getId(), exception.getMessage());
                charge.setNextRunAt(now.plusHours(1));
                recurringChargeRepository.save(charge);
            }
        }
    }

    void generate(RecurringCharge charge, OffsetDateTime now) {
        TeacherProfile profile = teacherProfileRepository.findById(charge.getTeacherProfileId()).orElse(null);
        if (profile == null) {
            charge.setActive(false);
            charge.setStoppedAt(now);
            recurringChargeRepository.save(charge);
            return;
        }
        PaymentOperation operation = createCharge(charge, profile);
        paymentService.linkToRecurringCharge(operation.getId(), charge.getId());
        charge.setLastOperationId(operation.getId());
        charge.setNextRunAt(nextRunAfter(now.atZoneSameInstant(ZONE).toLocalDate(), charge.getDayOfMonth()));
        recurringChargeRepository.save(charge);
        log.info("Cobro mensual generado recurringChargeId={} operationId={}", charge.getId(), operation.getId());
    }

    // Si el profesional conecto Mercado Pago despues, el cobro sale con Mercado Pago (y las otras formas en la pagina).
    private PaymentOperation createCharge(RecurringCharge charge, TeacherProfile profile) {
        boolean hasMercadoPago = profile.getMpAccessToken() != null && !profile.getMpAccessToken().isBlank();
        if (hasMercadoPago) {
            try {
                return paymentService.createOneTime(request(charge, PaymentProviderType.MERCADOPAGO));
            } catch (RuntimeException exception) {
                log.warn("Cobro mensual sin Mercado Pago por error recurringChargeId={}: {}", charge.getId(), exception.getMessage());
            }
        }
        return paymentService.createOneTime(request(charge, PaymentProviderType.MANUAL));
    }

    private static CreatePaymentRequest request(RecurringCharge charge, PaymentProviderType provider) {
        return new CreatePaymentRequest(
            charge.getTeacherProfileId(),
            provider,
            charge.getDescription(),
            charge.getAmount(),
            charge.getCurrency(),
            charge.getPayerEmail(),
            charge.getStudentUserId(),
            null
        );
    }

    /** Proxima ejecucion: el dia del cobro del mes siguiente a la fecha dada, a las 9 de la mañana de Montevideo. */
    static OffsetDateTime nextRunAfter(LocalDate from, int dayOfMonth) {
        LocalDate next = from.plusMonths(1).withDayOfMonth(Math.min(dayOfMonth, MAX_DAY));
        return next.atTime(RUN_AT).atZone(ZONE).toOffsetDateTime();
    }

    private static RecurringChargeView toView(RecurringCharge charge) {
        return new RecurringChargeView(
            charge.getId(),
            charge.getStudentUserId(),
            charge.getDescription(),
            charge.getAmount(),
            charge.getCurrency(),
            charge.getDayOfMonth(),
            charge.getNextRunAt(),
            charge.isActive()
        );
    }
}
