package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.EmailLoginCodeRepository;
import com.micuota.mvp.repository.TenantRepository;
import com.micuota.mvp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:emailcode;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EmailCodeAuthService.class, TenantAuthService.class, AuthSessionService.class, SessionActivityService.class, CrmLeadService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailCodeAuthServiceTest {

    @Autowired
    private EmailCodeAuthService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private EmailLoginCodeRepository codeRepository;

    @MockitoBean
    private OnboardingEmailService onboardingEmailService;
    @MockitoBean
    private SaasMetricsService saasMetricsService;

    private String requestAndCaptureCode(String email) {
        service.requestCode(email, "10.0.0.1");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(onboardingEmailService, org.mockito.Mockito.atLeastOnce()).sendLoginCode(eq(email.toLowerCase()), code.capture(), anyInt());
        return code.getValue();
    }

    private EmailCodeVerifyRequest verifyRequest(String email, String code, String fullName) {
        return new EmailCodeVerifyRequest(email, code, fullName, null, "CLASES", null);
    }

    @Test
    void newEmailAsksForNameThenCreatesTeacherAccountWithSameCode() {
        String email = "ana.nueva@example.com";
        String code = requestAndCaptureCode(email);

        EmailCodeVerifyResponse first = service.verify(verifyRequest(email, code, null));
        assertThat(first.status()).isEqualTo("NEEDS_NAME");

        EmailCodeVerifyResponse second = service.verify(verifyRequest(email, code, "Ana Pérez"));
        assertThat(second.status()).isEqualTo("SIGNED_IN");
        assertThat(second.created()).isTrue();
        assertThat(second.session().role()).isEqualTo(UserRole.TEACHER);
        assertThat(second.session().tenantSlug()).startsWith("ana-perez-");
        assertThat(second.session().token()).isNotBlank();
        verify(onboardingEmailService).sendNewTenantWelcomeEmail(eq(email), eq("Ana Pérez"), eq("Ana Pérez"), anyString());

        // El codigo ya se uso: no sirve para crear otra sesion.
        assertThatThrownBy(() -> service.verify(verifyRequest(email, code, "Ana Pérez")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existingEmailSignsInWithoutCreatingAnotherAccount() {
        String email = "beto.existente@example.com";
        service.verify(verifyRequest(email, requestAndCaptureCode(email), "Beto"));
        codeRepository.deleteAll();
        long tenantsBefore = tenantRepository.count();

        EmailCodeVerifyResponse login = service.verify(verifyRequest("Beto.Existente@example.com", requestAndCaptureCode(email), null));

        assertThat(login.status()).isEqualTo("SIGNED_IN");
        assertThat(login.created()).isFalse();
        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore);
        assertThat(userRepository.findAllByEmailOrderByIdAsc(email)).hasSize(1);
    }

    @Test
    void wrongCodeIsLimitedToFiveAttempts() {
        String email = "carla.intentos@example.com";
        String code = requestAndCaptureCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < EmailCodeAuthService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verify(verifyRequest(email, wrong, "Carla")))
                .isInstanceOf(IllegalArgumentException.class);
        }
        // Agotados los intentos, ni el codigo correcto entra.
        assertThatThrownBy(() -> service.verify(verifyRequest(email, code, "Carla")))
            .hasMessageContaining("muchas veces");
        assertThat(userRepository.findAllByEmailOrderByIdAsc(email)).isEmpty();
    }

    @Test
    void secondCodeRequestWithinCooldownIsRejected() {
        String email = "dani.apurado@example.com";
        requestAndCaptureCode(email);

        assertThatThrownBy(() -> service.requestCode(email, "10.0.0.1"))
            .hasMessageContaining("Esperá unos segundos");
    }

    @Test
    void failedEmailDeliveryIsReportedAndLeavesNoUsableCode() {
        String email = "eva.sinmail@example.com";
        doThrow(new IllegalStateException("smtp caido")).when(onboardingEmailService).sendLoginCode(eq(email), anyString(), anyInt());

        assertThatThrownBy(() -> service.requestCode(email, "10.0.0.1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No pudimos mandarte el código");
        assertThat(codeRepository.findFirstByEmailOrderByCreatedAtDesc(email)).isEmpty();
    }

    @Test
    void emailWithSeveralAccountsAsksWhichOne() {
        String email = "fede.multi@example.com";
        EmailCodeVerifyResponse created = service.verify(verifyRequest(email, requestAndCaptureCode(email), "Fede"));
        // Segunda cuenta con el mismo email en otro tenant (por ejemplo, alta hecha por el admin de plataforma).
        User original = userRepository.findAllByEmailOrderByIdAsc(email).get(0);
        User second = new User();
        var otherTenant = new com.micuota.mvp.domain.Tenant();
        otherTenant.setName("Escuela Fede");
        otherTenant.setSlug("escuela-fede");
        otherTenant.setCreatedAt(java.time.OffsetDateTime.now());
        otherTenant.setPlanCode("BASE");
        otherTenant.setTakeRateBps(350);
        otherTenant.setAdvancedDunningFeeBps(120);
        otherTenant.setRecoveryAutomationEnabled(false);
        otherTenant.setAdvancedAnalyticsEnabled(false);
        otherTenant.setIntegrationsEnabled(false);
        otherTenant = tenantRepository.save(otherTenant);
        second.setTenant(otherTenant);
        second.setEmail(email);
        second.setFullName(original.getFullName());
        second.setRole(UserRole.TENANT_ADMIN);
        second.setPasswordHash("x");
        userRepository.save(second);
        codeRepository.deleteAll();

        String code = requestAndCaptureCode(email);
        EmailCodeVerifyResponse choose = service.verify(verifyRequest(email, code, null));
        assertThat(choose.status()).isEqualTo("CHOOSE_ACCOUNT");
        assertThat(choose.accounts()).extracting(EmailCodeVerifyResponse.AccountOption::tenantSlug)
            .containsExactly(created.session().tenantSlug(), "escuela-fede");

        EmailCodeVerifyResponse chosen = service.verify(new EmailCodeVerifyRequest(email, code, null, null, null, "escuela-fede"));
        assertThat(chosen.status()).isEqualTo("SIGNED_IN");
        assertThat(chosen.session().tenantSlug()).isEqualTo("escuela-fede");
        assertThat(chosen.session().role()).isEqualTo(UserRole.TENANT_ADMIN);
    }
}
