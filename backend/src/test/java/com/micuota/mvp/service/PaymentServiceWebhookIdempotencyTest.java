package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.repository.CourseRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceWebhookIdempotencyTest {

    private static final String CREATION_RAW = "{\"providerResponse\":{\"id\":\"pref-1\",\"external_reference\":\"MC-abc\"}}";

    @Mock
    private TeacherProfileRepository teacherProfileRepository;
    @Mock
    private PaymentOperationRepository paymentOperationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private PaymentNotificationService paymentNotificationService;
    @Mock
    private SaasMetricsService saasMetricsService;
    @Mock
    private PaymentEventService paymentEventService;

    private PaymentService paymentService;
    private PaymentOperation operation;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
            teacherProfileRepository,
            paymentOperationRepository,
            userRepository,
            courseRepository,
            paymentNotificationService,
            saasMetricsService,
            paymentEventService,
            List.of()
        );
        operation = new PaymentOperation();
        ReflectionTestUtils.setField(operation, "id", 1L);
        operation.setTeacherId(10L);
        operation.setProvider(PaymentProviderType.MERCADOPAGO);
        operation.setFlowType(PaymentFlowType.ONE_TIME);
        operation.setAmount(new BigDecimal("100.00"));
        operation.setProcessingFeeAmount(new BigDecimal("3.50"));
        operation.setAdvancedFeatureFeeAmount(BigDecimal.ZERO);
        operation.setNetAmountForTeacher(new BigDecimal("96.50"));
        operation.setCurrency("UYU");
        operation.setProviderReference("pref-1");
        operation.setExternalReference("MC-abc");
        operation.setStatus(OperationStatus.CREATED);
        operation.setRawResponse(CREATION_RAW);
        operation.setCreatedAt(OffsetDateTime.now());
        operation.setRetryCount(0);
        operation.setReconciliationStatus("PENDING");

        lenient().when(paymentOperationRepository.findByExternalReferenceForUpdate("MC-abc")).thenReturn(Optional.of(operation));
        lenient().when(paymentOperationRepository.save(any(PaymentOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void duplicatedWebhookForSamePaymentRecordsStatusChangeOnlyOnce() {
        MercadoPagoPaymentDetails details = new MercadoPagoPaymentDetails("183995068253", new BigDecimal("6.09"), new BigDecimal("93.91"));

        paymentService.updateStatusByExternalReference("MC-abc", OperationStatus.SUCCESS, "{\"source\":\"mercadopago_webhook\",\"n\":1}", details);
        String rawAfterFirst = operation.getRawResponse();
        paymentService.updateStatusByExternalReference("MC-abc", OperationStatus.SUCCESS, "{\"source\":\"mercadopago_webhook\",\"n\":2}", details);

        verify(paymentEventService, times(1)).recordStatusChanged(eq(operation), eq(OperationStatus.CREATED), eq(OperationStatus.SUCCESS), anyString());
        verify(paymentEventService, times(2)).recordWebhookReceived(eq(operation), anyString());
        verify(saasMetricsService, times(1)).recordPaymentStatusChanged(OperationStatus.SUCCESS);
        assertThat(operation.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(operation.getRawResponse()).isEqualTo(rawAfterFirst);
    }

    @Test
    void storesRealMercadoPagoPaymentIdFeeAndNetAmount() {
        paymentService.updateStatusByExternalReference(
            "MC-abc",
            OperationStatus.SUCCESS,
            "{\"source\":\"mercadopago_webhook\"}",
            new MercadoPagoPaymentDetails("183995068253", new BigDecimal("6.09"), new BigDecimal("93.91"))
        );

        assertThat(operation.getMpPaymentId()).isEqualTo("183995068253");
        assertThat(operation.getMpFeeAmount()).isEqualByComparingTo("6.09");
        assertThat(operation.getMpNetReceivedAmount()).isEqualByComparingTo("93.91");
        // Los campos calculados por bps se mantienen hasta la Fase 1.
        assertThat(operation.getNetAmountForTeacher()).isEqualByComparingTo("96.50");
    }

    @Test
    void differentRejectedPaymentDoesNotRevertApprovedOperation() {
        paymentService.updateStatusByExternalReference("MC-abc", OperationStatus.SUCCESS, "{\"n\":1}",
            new MercadoPagoPaymentDetails("111", new BigDecimal("6.09"), new BigDecimal("93.91")));
        paymentService.updateStatusByExternalReference("MC-abc", OperationStatus.FAILURE, "{\"n\":2}",
            new MercadoPagoPaymentDetails("222", BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(operation.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(operation.getMpPaymentId()).isEqualTo("111");
        verify(paymentEventService, never()).recordStatusChanged(any(), eq(OperationStatus.SUCCESS), eq(OperationStatus.FAILURE), any());
    }

    @Test
    void rawResponseKeepsCreationAndOnlyLastEventInsteadOfNesting() {
        operation.setRawResponse("{\"previous\":{\"previous\":" + CREATION_RAW + ",\"event\":{\"n\":0}},\"event\":{\"n\":1}}");

        paymentService.appendRawResponseEvent(operation, "{\"n\":2}");
        paymentService.appendRawResponseEvent(operation, "{\"n\":3}");

        assertThat(operation.getRawResponse())
            .isEqualTo("{\"creation\":" + CREATION_RAW + ",\"lastEvent\":{\"n\":3}}");
    }

    @Test
    void mercadoPagoPaymentDetailsSumsOnlyCollectorFees() throws Exception {
        var payment = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"id":183995068253,"fee_details":[{"type":"mercadopago_fee","amount":6.09,"fee_payer":"collector"},
             {"type":"financing_fee","amount":2,"fee_payer":"payer"}],
             "transaction_details":{"net_received_amount":93.91}}
            """);

        MercadoPagoPaymentDetails details = MercadoPagoPaymentDetails.fromPayment(payment);

        assertThat(details.paymentId()).isEqualTo("183995068253");
        assertThat(details.feeAmount()).isEqualByComparingTo("6.09");
        assertThat(details.netReceivedAmount()).isEqualByComparingTo("93.91");
    }
}
