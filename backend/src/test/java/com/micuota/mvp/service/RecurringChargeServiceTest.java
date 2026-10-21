package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.RecurringCharge;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.RecurringChargeRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    "spring.datasource.url=jdbc:h2:mem:recurring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "app.base-url=https://api.micuota.test",
    "app.frontend-base-url=https://micuota.test"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecurringChargeService.class, PaymentService.class, PaymentEventService.class, ManualPaymentGateway.class, RecurringChargeServiceTest.Json.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecurringChargeServiceTest {

    @TestConfiguration
    static class Json {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private RecurringChargeService service;
    @Autowired
    private RecurringChargeRepository recurringChargeRepository;
    @Autowired
    private PaymentOperationRepository paymentOperationRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

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
        tenant.setName("Ana");
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
        teacherProfileRepository.save(profile);
        client = user("cliente-" + suffix + "@example.com", UserRole.STUDENT);
    }

    @Test
    void createsThisMonthsChargeAndSchedulesNextMonth() {
        PaymentOperation first = service.createFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, request());

        assertThat(first.getProvider()).isEqualTo(PaymentProviderType.MANUAL);
        assertThat(first.getRecurringChargeId()).isNotNull();
        RecurringCharge charge = recurringChargeRepository.findById(first.getRecurringChargeId()).orElseThrow();
        assertThat(charge.isActive()).isTrue();
        assertThat(charge.getLastOperationId()).isEqualTo(first.getId());
        assertThat(charge.getNextRunAt()).isAfter(OffsetDateTime.now().plusDays(25));
        assertThat(service.listForBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER)).hasSize(1);
    }

    @Test
    void generatesTheNextChargeWhenDueAndStopsWhenAsked() {
        PaymentOperation first = service.createFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, request());
        RecurringCharge charge = recurringChargeRepository.findById(first.getRecurringChargeId()).orElseThrow();
        charge.setNextRunAt(OffsetDateTime.now().minusMinutes(1));
        recurringChargeRepository.save(charge);

        service.generateDueCharges();

        RecurringCharge after = recurringChargeRepository.findById(charge.getId()).orElseThrow();
        assertThat(after.getLastOperationId()).isNotEqualTo(first.getId());
        PaymentOperation second = paymentOperationRepository.findById(after.getLastOperationId()).orElseThrow();
        assertThat(second.getRecurringChargeId()).isEqualTo(charge.getId());
        assertThat(second.getStatus()).isEqualTo(OperationStatus.CREATED);
        assertThat(second.getAmount()).isEqualByComparingTo("1800");
        assertThat(after.getNextRunAt()).isAfter(OffsetDateTime.now().plusDays(25));

        service.stopFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, charge.getId());
        RecurringCharge stopped = recurringChargeRepository.findById(charge.getId()).orElseThrow();
        stopped.setNextRunAt(OffsetDateTime.now().minusMinutes(1));
        recurringChargeRepository.save(stopped);
        long before = paymentOperationRepository.count();
        service.generateDueCharges();
        assertThat(paymentOperationRepository.count()).isEqualTo(before);
        assertThat(service.listForBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER)).isEmpty();
    }

    @Test
    void othersCannotStopTheCharge() {
        PaymentOperation first = service.createFromBackoffice(tenant.getId(), professional.getId(), UserRole.TEACHER, request());
        User stranger = user("otro-" + System.nanoTime() + "@example.com", UserRole.TEACHER);
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(stranger);
        profile.setDisplayName("Otro");
        teacherProfileRepository.save(profile);

        assertThatThrownBy(() -> service.stopFromBackoffice(tenant.getId(), stranger.getId(), UserRole.TEACHER, first.getRecurringChargeId()))
            .hasMessageContaining("no encontrado");
    }

    @Test
    void nextRunKeepsTheDayAndCapsShortMonths() {
        assertThat(RecurringChargeService.nextRunAfter(LocalDate.of(2026, 1, 15), 15).toLocalDate()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(RecurringChargeService.nextRunAfter(LocalDate.of(2026, 1, 31), 31).toLocalDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RecurringChargeService.nextRunAfter(LocalDate.of(2026, 12, 10), 10).toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 10));
    }

    private CreateBackofficePaymentRequest request() {
        return new CreateBackofficePaymentRequest(PaymentProviderType.MANUAL, "Cuota mensual", new BigDecimal("1800"), "UYU",
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
