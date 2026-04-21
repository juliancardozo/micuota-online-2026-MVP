package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentEvent;
import com.micuota.mvp.domain.PaymentEventType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.repository.PaymentEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventService {

    private final PaymentEventRepository paymentEventRepository;

    public PaymentEventService(PaymentEventRepository paymentEventRepository) {
        this.paymentEventRepository = paymentEventRepository;
    }

    @Transactional
    public PaymentEvent recordPaymentCreated(PaymentOperation operation, String rawPayload) {
        return saveEvent(operation, PaymentEventType.PAYMENT_CREATED, null, operation.getStatus(), rawPayload);
    }

    @Transactional
    public PaymentEvent recordPaymentLinkSent(PaymentOperation operation, String rawPayload) {
        return saveEvent(operation, PaymentEventType.PAYMENT_LINK_SENT, operation.getStatus(), operation.getStatus(), rawPayload);
    }

    @Transactional
    public PaymentEvent recordWebhookReceived(PaymentOperation operation, String rawPayload) {
        return saveEvent(operation, PaymentEventType.WEBHOOK_RECEIVED, operation.getStatus(), operation.getStatus(), rawPayload);
    }

    @Transactional
    public PaymentEvent recordStatusChanged(
        PaymentOperation operation,
        OperationStatus previousStatus,
        OperationStatus nextStatus,
        String rawPayload
    ) {
        return saveEvent(operation, PaymentEventType.PAYMENT_STATUS_CHANGED, previousStatus, nextStatus, rawPayload);
    }

    @Transactional(readOnly = true)
    public List<PaymentEventView> lastEventsForOperation(Long operationId) {
        return paymentEventRepository.findTop50ByOperationIdOrderByCreatedAtDesc(operationId)
            .stream()
            .map(this::toView)
            .toList();
    }

    private PaymentEvent saveEvent(
        PaymentOperation operation,
        PaymentEventType eventType,
        OperationStatus statusFrom,
        OperationStatus statusTo,
        String rawPayload
    ) {
        PaymentEvent event = new PaymentEvent();
        event.setOperationId(operation.getId());
        event.setTeacherId(operation.getTeacherId());
        event.setStudentUserId(operation.getStudentUserId());
        event.setCourseId(operation.getCourseId());
        event.setProvider(operation.getProvider());
        event.setFlowType(operation.getFlowType());
        event.setEventType(eventType);
        event.setStatusFrom(statusFrom);
        event.setStatusTo(statusTo);
        event.setAmount(operation.getAmount());
        event.setCurrency(operation.getCurrency());
        event.setProviderReference(operation.getProviderReference());
        event.setRawPayload(rawPayload == null || rawPayload.isBlank() ? "{}" : rawPayload);
        event.setCreatedAt(OffsetDateTime.now());
        return paymentEventRepository.save(event);
    }

    private PaymentEventView toView(PaymentEvent event) {
        return new PaymentEventView(
            event.getId(),
            event.getOperationId(),
            event.getTeacherId(),
            event.getStudentUserId(),
            event.getCourseId(),
            event.getProvider(),
            event.getFlowType(),
            event.getEventType(),
            event.getStatusFrom(),
            event.getStatusTo(),
            event.getAmount(),
            event.getCurrency(),
            event.getProviderReference(),
            event.getRawPayload(),
            event.getCreatedAt()
        );
    }
}
