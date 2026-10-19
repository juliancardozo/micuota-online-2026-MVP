package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentEventType;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.repository.PaymentEventRepository;
import com.micuota.mvp.repository.PaymentOperationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simula la entrega simultanea de la misma notificacion de Mercado Pago (caso real: pago 183995068253 notificado dos
 * veces con menos de 1 s de diferencia) contra H2 real, sin transaccion envolvente del test.
 */
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:webhookconcurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentService.class, PaymentEventService.class, PaymentWebhookConcurrencyIntegrationTest.Gateways.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentWebhookConcurrencyIntegrationTest {

    private static final int CONCURRENT_WEBHOOKS = 8;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentOperationRepository paymentOperationRepository;
    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @MockitoBean
    private PaymentNotificationService paymentNotificationService;
    @MockitoBean
    private SaasMetricsService saasMetricsService;

    @TestConfiguration
    static class Gateways {
        @Bean
        PaymentProviderGateway noopGateway() {
            return new PaymentProviderGateway() {
                @Override
                public PaymentProviderType provider() {
                    return PaymentProviderType.MERCADOPAGO;
                }

                @Override
                public ProviderCheckoutResult createCheckout(
                    PaymentFlowType flowType,
                    CreatePaymentRequest request,
                    TeacherProviderCredentials credentials,
                    String callbackBaseUrl
                ) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Test
    void concurrentIdenticalWebhooksProduceSingleStatusChange() throws Exception {
        PaymentOperation operation = paymentOperationRepository.save(newOperation("MC-concurrent"));
        MercadoPagoPaymentDetails details = new MercadoPagoPaymentDetails("183995068253", new BigDecimal("6.09"), new BigDecimal("93.91"));

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WEBHOOKS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentOperation>> results = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_WEBHOOKS; i++) {
                String rawEvent = "{\"source\":\"mercadopago_webhook\",\"delivery\":" + i + "}";
                results.add(executor.submit(() -> {
                    start.await();
                    return paymentService.updateStatusByExternalReference("MC-concurrent", OperationStatus.SUCCESS, rawEvent, details);
                }));
            }
            start.countDown();
            for (Future<PaymentOperation> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(OperationStatus.SUCCESS);
            }
        } finally {
            executor.shutdownNow();
        }

        var events = paymentEventRepository.findTop50ByOperationIdOrderByCreatedAtDesc(operation.getId());
        assertThat(events).filteredOn(event -> event.getEventType() == PaymentEventType.PAYMENT_STATUS_CHANGED).hasSize(1);
        assertThat(events).filteredOn(event -> event.getEventType() == PaymentEventType.WEBHOOK_RECEIVED).hasSize(CONCURRENT_WEBHOOKS);

        PaymentOperation stored = paymentOperationRepository.findById(operation.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(stored.getMpPaymentId()).isEqualTo("183995068253");
        assertThat(stored.getMpFeeAmount()).isEqualByComparingTo("6.09");
        assertThat(stored.getMpNetReceivedAmount()).isEqualByComparingTo("93.91");
    }

    private PaymentOperation newOperation(String externalReference) {
        PaymentOperation operation = new PaymentOperation();
        operation.setTeacherId(1L);
        operation.setProvider(PaymentProviderType.MERCADOPAGO);
        operation.setFlowType(PaymentFlowType.ONE_TIME);
        operation.setAmount(new BigDecimal("100.00"));
        operation.setProcessingFeeAmount(new BigDecimal("3.50"));
        operation.setAdvancedFeatureFeeAmount(BigDecimal.ZERO);
        operation.setNetAmountForTeacher(new BigDecimal("96.50"));
        operation.setCurrency("UYU");
        operation.setDescription("Cuota concurrencia");
        operation.setCheckoutUrl("https://example.test/checkout");
        operation.setProviderReference("pref-concurrent");
        operation.setExternalReference(externalReference);
        operation.setStatus(OperationStatus.CREATED);
        operation.setRawResponse("{\"providerResponse\":{\"external_reference\":\"" + externalReference + "\"}}");
        operation.setCreatedAt(OffsetDateTime.now());
        operation.setRetryCount(0);
        operation.setReconciliationStatus("PENDING");
        return operation;
    }
}
