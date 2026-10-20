package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:backoffice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BackofficeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BackofficeServiceTest {

    @Autowired
    private BackofficeService service;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @MockitoBean
    private PaymentKpiService paymentKpiService;
    @MockitoBean
    private MercadoPagoOAuthService mercadoPagoOAuthService;
    @MockitoBean
    private OnboardingEmailService onboardingEmailService;

    private Tenant academy;

    @BeforeEach
    void setUp() {
        Tenant tenant = new Tenant();
        tenant.setName("Escuela Norte");
        tenant.setSlug("escuela-norte-" + System.nanoTime());
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setPlanCode("BASE");
        tenant.setTakeRateBps(350);
        tenant.setAdvancedDunningFeeBps(120);
        tenant.setRecoveryAutomationEnabled(false);
        tenant.setAdvancedAnalyticsEnabled(false);
        tenant.setIntegrationsEnabled(false);
        academy = tenantRepository.save(tenant);
    }

    @Test
    void teacherIsCreatedWithoutPasswordInvitedAndListedWithMercadoPagoState() {
        BackofficeUserView created = service.createUser(academy.getId(),
            new CreateBackofficeUserRequest("Pedro Silva", "Pedro@Example.com", null, UserRole.TEACHER));

        assertThat(created.email()).isEqualTo("pedro@example.com");
        assertThat(created.teacherProfileId()).isNotNull();
        assertThat(created.mercadoPagoConnection()).isEqualTo("NONE");
        verify(onboardingEmailService).sendTeamInviteEmail(eq("pedro@example.com"), eq("Pedro Silva"), eq("Escuela Norte"), eq(false));

        TeacherProfile profile = teacherProfileRepository.findById(created.teacherProfileId()).orElseThrow();
        profile.setMpAccessToken("APP_USR-oauth");
        profile.setMpOauthConnected(true);
        teacherProfileRepository.save(profile);

        List<BackofficeUserView> teachers = service.listUsers(academy.getId(), UserRole.TEACHER);
        assertThat(teachers).singleElement()
            .satisfies(view -> assertThat(view.mercadoPagoConnection()).isEqualTo("OAUTH"));
    }

    @Test
    void studentIsCreatedWithoutInvitation() {
        BackofficeUserView created = service.createUser(academy.getId(),
            new CreateBackofficeUserRequest("Laura Gómez", "laura@example.com", "", UserRole.STUDENT));

        assertThat(created.teacherProfileId()).isNull();
        assertThat(created.mercadoPagoConnection()).isNull();
        verify(onboardingEmailService, never()).sendTeamInviteEmail(anyString(), anyString(), anyString(), eq(false));
    }
}
