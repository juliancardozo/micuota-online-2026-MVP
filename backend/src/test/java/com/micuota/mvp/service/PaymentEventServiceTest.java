package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentEvent;
import com.micuota.mvp.domain.PaymentEventType;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.repository.PaymentEventRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentEventServiceTest {

    @Mock
    private PaymentEventRepository paymentEventRepository;

    private PaymentEventService paymentEventService;

    @BeforeEach
    void setUp() {
        paymentEventService = new PaymentEventService(paymentEventRepository);
    }

    @Test
    void recordPaymentCreatedSnapshotsOperationContext() {
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentOperation operation = operation();
        PaymentEvent event = paymentEventService.recordPaymentCreated(operation, "{\"provider\":\"mercadopago\"}");

        assertThat(event.getOperationId()).isEqualTo(10L);
        assertThat(event.getTeacherId()).isEqualTo(20L);
        assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_CREATED);
        assertThat(event.getStatusFrom()).isNull();
        assertThat(event.getStatusTo()).isEqualTo(OperationStatus.CREATED);
        assertThat(event.getProviderReference()).isEqualTo("pref-123");
        assertThat(event.getRawPayload()).contains("mercadopago");
    }

    @Test
    void lastEventsForOperationMapsToViews() {
        PaymentEvent event = new PaymentEvent();
        ReflectionTestUtils.setField(event, "id", 99L);
        event.setOperationId(10L);
        event.setTeacherId(20L);
        event.setProvider(PaymentProviderType.MERCADOPAGO);
        event.setFlowType(PaymentFlowType.ONE_TIME);
        event.setEventType(PaymentEventType.PAYMENT_STATUS_CHANGED);
        event.setStatusFrom(OperationStatus.CREATED);
        event.setStatusTo(OperationStatus.SUCCESS);
        event.setAmount(new BigDecimal("1200.00"));
        event.setCurrency("UYU");
        event.setProviderReference("pref-123");
        event.setRawPayload("{}");
        event.setCreatedAt(OffsetDateTime.now());
        when(paymentEventRepository.findTop50ByOperationIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(event));

        List<PaymentEventView> views = paymentEventService.lastEventsForOperation(10L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(99L);
        assertThat(views.get(0).eventType()).isEqualTo(PaymentEventType.PAYMENT_STATUS_CHANGED);
        assertThat(views.get(0).statusTo()).isEqualTo(OperationStatus.SUCCESS);
    }

    private PaymentOperation operation() {
        PaymentOperation operation = new PaymentOperation();
        ReflectionTestUtils.setField(operation, "id", 10L);
        operation.setTeacherId(20L);
        operation.setStudentUserId(30L);
        operation.setCourseId(40L);
        operation.setProvider(PaymentProviderType.MERCADOPAGO);
        operation.setFlowType(PaymentFlowType.ONE_TIME);
        operation.setStatus(OperationStatus.CREATED);
        operation.setAmount(new BigDecimal("1200.00"));
        operation.setCurrency("UYU");
        operation.setProviderReference("pref-123");
        return operation;
    }
}
