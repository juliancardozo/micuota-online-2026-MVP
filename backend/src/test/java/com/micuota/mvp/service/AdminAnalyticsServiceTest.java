package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.micuota.mvp.config.PlatformAdminSeeder;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:adminanalytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminAnalyticsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminAnalyticsServiceTest {

    @Autowired
    private AdminAnalyticsService service;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;
    @Autowired
    private PaymentOperationRepository paymentOperationRepository;

    private TeacherProfile adminProfile;
    private TeacherProfile testTeacher;
    private TeacherProfile oldTeacher;

    @BeforeEach
    void setUp() {
        paymentOperationRepository.deleteAll();
        teacherProfileRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant platform = tenant(PlatformAdminSeeder.PLATFORM_TENANT_SLUG);
        adminProfile = profile(user(platform, "admin@micuota.online", UserRole.ADMIN), false);

        Tenant test = tenant("profesor-prueba-800");
        testTeacher = profile(user(test, "prueba@example.com", UserRole.TEACHER), true);
        user(test, "alumno@example.com", UserRole.STUDENT);

        Tenant old = tenant("demo-academia");
        oldTeacher = profile(user(old, "teacher@micuota.online", UserRole.TEACHER), false);
        oldTeacher.setMpAccessToken("APP_USR-manual");
        teacherProfileRepository.save(oldTeacher);

        payment(testTeacher, "UYU", "1000.00", OperationStatus.SUCCESS, "35.00");
        payment(testTeacher, "USD", "20.00", OperationStatus.SUCCESS, "0.70");
        payment(testTeacher, "UYU", "500.00", OperationStatus.CREATED, null);
        payment(oldTeacher, "UYU", "800.00", OperationStatus.SUCCESS, null);
        payment(adminProfile, "UYU", "999.00", OperationStatus.SUCCESS, null);
    }

    @Test
    void overviewExcludesPlatformAccountAndSeparatesCurrencies() {
        AdminOverviewView overview = service.buildOverview();

        assertThat(overview.accounts()).isEqualTo(2);
        assertThat(overview.professionals()).isEqualTo(2);
        assertThat(overview.professionalsConnectedViaOAuth()).isEqualTo(1);
        assertThat(overview.professionalsWithManualToken()).isEqualTo(1);
        assertThat(overview.students()).isEqualTo(1);
        // El cobro del perfil del administrador no cuenta.
        assertThat(overview.paymentsCreated()).isEqualTo(4);
        assertThat(overview.paymentsPaid()).isEqualTo(3);
        assertThat(overview.paymentsPending()).isEqualTo(1);
        assertThat(overview.totals()).extracting(AdminOverviewView.CurrencyTotal::currency).containsExactly("USD", "UYU");
        AdminOverviewView.CurrencyTotal uyu = overview.totals().get(1);
        assertThat(uyu.paidAmount()).isEqualByComparingTo("1800.00");
        assertThat(uyu.commissionCollected()).isEqualByComparingTo("35.00");
        assertThat(overview.recentAccounts()).extracting(AdminOverviewView.RecentAccount::tenantSlug)
            .containsExactlyInAnyOrder("profesor-prueba-800", "demo-academia");
    }

    @Test
    void resetKeepsOnlyTheChosenAccountThePlatformAndTheAdmin() {
        AdminResetResult result = service.resetData(new AdminResetRequest(List.of("profesor-prueba-800"), true));

        assertThat(result.deletedTenants()).isEqualTo(1);
        assertThat(result.deletedUsers()).isEqualTo(1);
        assertThat(result.deletedPayments()).isEqualTo(5);
        assertThat(result.keptTenantSlugs()).containsExactly(PlatformAdminSeeder.PLATFORM_TENANT_SLUG, "profesor-prueba-800");
        assertThat(paymentOperationRepository.count()).isZero();
        assertThat(tenantRepository.findBySlug("demo-academia")).isEmpty();
        assertThat(userRepository.findAllByRole(UserRole.ADMIN)).hasSize(1);
        // Queda solo el profesional de prueba, con su conexion de Mercado Pago intacta.
        assertThat(teacherProfileRepository.findAll()).singleElement()
            .satisfies(profile -> assertThat(profile.isMpOauthConnected()).isTrue());
        assertThat(userRepository.findAllByEmailOrderByIdAsc("alumno@example.com")).hasSize(1);
    }

    @Test
    void resetCanKeepPaymentsOfKeptAccounts() {
        AdminResetResult result = service.resetData(new AdminResetRequest(List.of("profesor-prueba-800"), false));

        // Se van el cobro de la cuenta borrada y el del perfil del administrador.
        assertThat(result.deletedPayments()).isEqualTo(2);
        assertThat(paymentOperationRepository.findAll())
            .allSatisfy(payment -> assertThat(payment.getTeacherId()).isEqualTo(testTeacher.getId()));
    }

    @Test
    void resetRejectsUnknownAccounts() {
        assertThatThrownBy(() -> service.resetData(new AdminResetRequest(List.of("no-existe"), true)))
            .hasMessageContaining("Cuenta no encontrada");
        assertThat(tenantRepository.count()).isEqualTo(3);
    }

    private Tenant tenant(String slug) {
        Tenant tenant = new Tenant();
        tenant.setName("Cuenta " + slug);
        tenant.setSlug(slug);
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setPlanCode("BASE");
        tenant.setTakeRateBps(350);
        tenant.setAdvancedDunningFeeBps(120);
        tenant.setRecoveryAutomationEnabled(false);
        tenant.setAdvancedAnalyticsEnabled(false);
        tenant.setIntegrationsEnabled(false);
        return tenantRepository.save(tenant);
    }

    private User user(Tenant tenant, String email, UserRole role) {
        User user = new User();
        user.setTenant(tenant);
        user.setEmail(email);
        user.setFullName(email);
        user.setRole(role);
        user.setPasswordHash("x");
        return userRepository.save(user);
    }

    private TeacherProfile profile(User user, boolean oauth) {
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setDisplayName(user.getFullName());
        if (oauth) {
            profile.setMpAccessToken("APP_USR-oauth");
            profile.setMpOauthConnected(true);
        }
        return teacherProfileRepository.save(profile);
    }

    private void payment(TeacherProfile teacher, String currency, String amount, OperationStatus status, String fee) {
        PaymentOperation operation = new PaymentOperation();
        operation.setTeacherId(teacher.getId());
        operation.setProvider(PaymentProviderType.MERCADOPAGO);
        operation.setFlowType(PaymentFlowType.ONE_TIME);
        operation.setAmount(new BigDecimal(amount));
        operation.setProcessingFeeAmount(BigDecimal.ZERO);
        operation.setAdvancedFeatureFeeAmount(BigDecimal.ZERO);
        operation.setNetAmountForTeacher(new BigDecimal(amount));
        operation.setMarketplaceFeeAmount(fee == null ? null : new BigDecimal(fee));
        operation.setCurrency(currency);
        operation.setDescription("Clase");
        operation.setCheckoutUrl("https://mp/checkout");
        operation.setProviderReference(UUID.randomUUID().toString());
        operation.setStatus(status);
        operation.setRawResponse("{}");
        operation.setCreatedAt(OffsetDateTime.now());
        operation.setRetryCount(0);
        operation.setReconciliationStatus("PENDING");
        paymentOperationRepository.save(operation);
    }
}
