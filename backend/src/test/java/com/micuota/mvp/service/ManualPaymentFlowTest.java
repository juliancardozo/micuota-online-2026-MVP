package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaidMethod;
import com.micuota.mvp.domain.PaymentEventType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.PaymentEventRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
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

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:manualpayments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "app.base-url=https://api.micuota.test",
    "app.frontend-base-url=https://micuota.test"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentService.class, PaymentEventService.class, ManualPaymentGateway.class, ManualPaymentFlowTest.Json.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ManualPaymentFlowTest {

    @TestConfiguration
    static class Json {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @MockitoBean
    private PaymentNotificationService paymentNotificationService;
    @MockitoBean
    private SaasMetricsService saasMetricsService;

    private Tenant tenant;
    private User professional;
    private User client;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        tenant = new Tenant();
        tenant.setName("Psicóloga Ana");
        tenant.setSlug("ana-" + suffix);
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setPlanCode("BASE");
        tenant.setTakeRateBps(350);
        tenant.setAdvancedDunningFeeBps(120);
        tenant.setRecoveryAutomationEnabled(false);
        tenant.setAdvancedAnalyticsEnabled(false);
        tenant.setIntegrationsEnabled(false);
        tenant = tenantRepository.save(tenant);
        professional = user("ana-" + suffix + "@example.com", UserRole.TEACHER);
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(professional);
        profile.setDisplayName("Ana");
        profile.setTransferBankName("BROU");
        profile.setTransferAccountNumber("001234567-00001");
        teacherProfileRepository.save(profile);
        client = user("cliente-" + suffix + "@example.com", UserRole.STUDENT);
    }

    @Test
    void manualChargeHasNoCommissionAndLinksToThePublicPage() {
        PaymentOperation operation = createManual();

        assertThat(operation.getProvider()).isEqualTo(PaymentProviderType.MANUAL);
        assertThat(operation.getCheckoutUrl()).isEqualTo("https://micuota.test/pago.html?operationId=" + operation.getId());
        assertThat(operation.getProcessingFeeAmount()).isEqualByComparingTo("0");
        assertThat(operation.getNetAmountForTeacher()).isEqualByComparingTo("2000");
        assertThat(operation.getMarketplaceFeeAmount()).isNull();

        PaymentPublicView publicView = paymentService.getPublicView(operation.getId());
        assertThat(publicView.checkoutUrl()).isNull();
        assertThat(publicView.transferAccountNumber()).isEqualTo("001234567-00001");
    }

    @Test
    void monthlyChargesNeedMercadoPago() {
        assertThatThrownBy(() -> paymentService.createSubscriptionForBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, request()))
            .hasMessageContaining("necesitan Mercado Pago");
    }

    @Test
    void professionalMarksPaidAndCanUndoIt() {
        PaymentOperation operation = createManual();

        PaymentOperation paid = paymentService.markPaidFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER,
            operation.getId(), new MarkPaymentPaidRequest(PaidMethod.CASH, " En mano "));
        assertThat(paid.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(paid.getPaidMethod()).isEqualTo(PaidMethod.CASH);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getPaidNote()).isEqualTo("En mano");
        assertThat(paymentEventRepository.findTop50ByOperationIdOrderByCreatedAtDesc(operation.getId()))
            .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_STATUS_CHANGED));
        assertThat(paymentService.getPublicView(operation.getId()).paidMethod()).isEqualTo(PaidMethod.CASH);

        assertThatThrownBy(() -> paymentService.markPaidFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER,
            operation.getId(), new MarkPaymentPaidRequest(PaidMethod.TRANSFER, null)))
            .hasMessageContaining("ya figura como pagado");

        PaymentOperation undone = paymentService.markUnpaidFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, operation.getId());
        assertThat(undone.getStatus()).isEqualTo(OperationStatus.CREATED);
        assertThat(undone.getPaidMethod()).isNull();
    }

    @Test
    void mercadoPagoCannotBeMarkedByHandAndOthersCannotTouchTheCharge() {
        PaymentOperation operation = createManual();

        assertThatThrownBy(() -> paymentService.markPaidFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER,
            operation.getId(), new MarkPaymentPaidRequest(PaidMethod.MERCADOPAGO, null)))
            .hasMessageContaining("se marcan solos");

        User stranger = user("otro-" + System.nanoTime() + "@example.com", UserRole.TEACHER);
        TeacherProfile strangerProfile = new TeacherProfile();
        strangerProfile.setUser(stranger);
        strangerProfile.setDisplayName("Otro");
        teacherProfileRepository.save(strangerProfile);
        assertThatThrownBy(() -> paymentService.markPaidFromBackoffice(tenant.getId(), stranger.getId(), UserRole.TEACHER,
            operation.getId(), new MarkPaymentPaidRequest(PaidMethod.CASH, null)))
            .hasMessageContaining("Operacion no encontrada");
    }

    private PaymentOperation createManual() {
        return paymentService.createOneTimeForBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, request());
    }

    private CreateBackofficePaymentRequest request() {
        return new CreateBackofficePaymentRequest(PaymentProviderType.MANUAL, "Sesión de octubre", new BigDecimal("2000"), "UYU",
            client.getEmail(), client.getId(), null, null);
    }

    private User user(String email, UserRole role) {
        User user = new User();
        user.setTenant(tenant);
        user.setEmail(email);
        user.setFullName(email);
        user.setRole(role);
        user.setPasswordHash("x");
        return userRepository.save(user);
    }
}
