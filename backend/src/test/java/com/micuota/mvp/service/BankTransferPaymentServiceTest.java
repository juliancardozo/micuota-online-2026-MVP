package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.integration.prometeo.config.PrometeoProperties;
import com.micuota.mvp.integration.prometeo.service.PrometeoBankingClient;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.service.BankTransferPaymentService.BankStep;
import com.micuota.mvp.service.BankTransferPaymentService.BankStepRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankTransferPaymentServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Mock
    private PrometeoBankingClient client;
    @Mock
    private PaymentOperationRepository paymentOperationRepository;
    @Mock
    private TeacherProfileRepository teacherProfileRepository;
    @Mock
    private PaymentService paymentService;

    private BankTransferPaymentService service;
    private PaymentOperation operation;

    @BeforeEach
    void setUp() throws Exception {
        PrometeoProperties properties = new PrometeoProperties();
        properties.setSandbox(true);
        properties.setCountry("UY");
        properties.setSandboxDestinationInstitution("012");
        service = new BankTransferPaymentService(client, properties, paymentOperationRepository, teacherProfileRepository, paymentService);

        operation = new PaymentOperation();
        operation.setTeacherId(9L);
        operation.setAmount(new BigDecimal("1800"));
        operation.setCurrency("UYU");
        operation.setFlowType(PaymentFlowType.ONE_TIME);
        operation.setStatus(OperationStatus.CREATED);
        TeacherProfile payee = new TeacherProfile();
        payee.setTransferBankName("BROU");
        payee.setTransferAccountNumber("001234567 00001");
        payee.setTransferAccountHolder("Ana Pérez");

        lenient().when(client.isConfigured()).thenReturn(true);
        lenient().when(paymentOperationRepository.findById(1L)).thenReturn(Optional.of(operation));
        lenient().when(teacherProfileRepository.findById(9L)).thenReturn(Optional.of(payee));
        lenient().when(client.providers()).thenReturn(node("""
            {"status":"success","providers":[{"code":"itau_corp_uy","name":"Itaú","country":"UY"},{"code":"test","name":"Test","country":"UY"},{"code":"bcp_pe","name":"BCP","country":"PE"}]}"""));
        lenient().when(client.providerDetail("test")).thenReturn(node("""
            {"status":"success","provider":{"auth_fields":[
              {"name":"username","type":"text","interactive":false,"optional":false,"label_es":"Usuario"},
              {"name":"password","type":"password","interactive":false,"optional":false,"label_es":"Contraseña"},
              {"name":"otp","type":"text","interactive":true,"optional":false,"label_es":"Código"}]}}"""));
        lenient().when(client.accounts("sess-1")).thenReturn(node("""
            {"status":"success","accounts":[{"id":"a1","name":"Caja de ahorro","number":"12345678","currency":"UYU"},{"id":"a2","name":"Dólares","number":"87654321","currency":"USD"}]}"""));
    }

    @Test
    void optionsListOnlyUruguayanBanksAndTheSandboxBank() {
        BankTransferPaymentService.BankOptions options = service.options(1L);

        assertThat(options.available()).isTrue();
        assertThat(options.providers()).extracting(BankTransferPaymentService.BankProvider::code).containsExactly("itau_corp_uy", "test");
        assertThat(service.providerFields("test")).extracting(BankTransferPaymentService.BankAuthField::name).containsExactly("username", "password");
    }

    @Test
    void paysFromTheClientsBankAndMarksTheChargePaid() throws Exception {
        when(client.login(anyMap())).thenReturn(node("{\"status\":\"logged_in\",\"key\":\"sess-1\"}"));
        when(client.preprocessTransfer(eq("sess-1"), anyMap())).thenReturn(node("""
            {"status":"success","result":{"approved":true,"message":"Ana Pérez","request_id":"req-7","authorization_devices":[{"type":"pin","data":[]}]}}"""));
        when(client.confirmTransfer(eq("sess-1"), anyMap())).thenReturn(node("{\"status\":\"success\",\"transfer\":{\"operation_id\":\"OP-99\"}}"));

        BankStep accounts = service.login(1L, request(null, Map.of("username", "12345", "password", "gfdsa")));
        assertThat(accounts.step()).isEqualTo("ACCOUNTS");
        assertThat(accounts.accounts()).singleElement().satisfies(account -> {
            assertThat(account.currency()).isEqualTo("UYU");
            assertThat(account.maskedNumber()).isEqualTo("•••• 5678");
        });

        BankStep authorize = service.transfer(1L, new BankStepRequest(accounts.session(), null, null, null, null, "a1", null, null, null));
        assertThat(authorize.step()).isEqualTo("AUTHORIZE");
        assertThat(authorize.destinationName()).isEqualTo("Ana Pérez");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> transfer = ArgumentCaptor.forClass(Map.class);
        verify(client).preprocessTransfer(eq("sess-1"), transfer.capture());
        assertThat(transfer.getValue())
            .containsEntry("origin_account", "12345678")
            .containsEntry("destination_institution", "012")
            .containsEntry("destination_account", "00123456700001")
            .containsEntry("amount", "1800.00")
            .containsEntry("currency", "UYU")
            .containsEntry("destination_owner_name", "Ana Pérez");

        BankStep done = service.confirm(1L, new BankStepRequest(accounts.session(), null, null, null, null, null, "pin", "1234", null));
        assertThat(done.step()).isEqualTo("DONE");
        verify(paymentService).markPaidByBankTransfer(1L, "OP-99");
        verify(client).logout("sess-1");
        // La sesion ya no sirve despues de pagar.
        assertThatThrownBy(() -> service.confirm(1L, new BankStepRequest(accounts.session(), null, null, null, null, null, "pin", "1234", null)))
            .hasMessageContaining("Empezá de nuevo");
    }

    @Test
    void wrongCredentialsAndMissingFieldsAreExplained() throws Exception {
        assertThatThrownBy(() -> service.login(1L, request(null, Map.of("username", "12345"))))
            .hasMessageContaining("contraseña");
        when(client.login(anyMap())).thenReturn(node("{\"status\":\"wrong_credentials\"}"));
        assertThatThrownBy(() -> service.login(1L, request(null, Map.of("username", "12345", "password", "mala"))))
            .hasMessageContaining("no son correctos");
    }

    @Test
    void bankChallengeIsAnsweredWithTheSameSession() throws Exception {
        when(client.login(anyMap())).thenReturn(node("{\"status\":\"interaction_required\",\"key\":\"sess-1\",\"context\":\"Ingresá el código SMS\",\"field\":\"otp\"}"));
        when(client.answerLogin(eq("sess-1"), anyMap())).thenReturn(node("{\"status\":\"logged_in\",\"key\":\"sess-1\"}"));

        BankStep challenge = service.login(1L, request(null, Map.of("username", "12345", "password", "gfdsa")));
        assertThat(challenge.step()).isEqualTo("CHALLENGE");
        assertThat(challenge.challenge()).isEqualTo("Ingresá el código SMS");

        BankStep accounts = service.answerChallenge(1L, new BankStepRequest(challenge.session(), null, null, "4321", null, null, null, null, null));
        assertThat(accounts.step()).isEqualTo("ACCOUNTS");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        verify(client).answerLogin(eq("sess-1"), fields.capture());
        assertThat(fields.getValue()).containsEntry("otp", "4321").containsEntry("provider", "test");
    }

    @Test
    void failedConfirmationDoesNotMarkPaidAndSessionsAreTiedToTheirCharge() throws Exception {
        when(client.login(anyMap())).thenReturn(node("{\"status\":\"logged_in\",\"key\":\"sess-1\"}"));
        when(client.preprocessTransfer(eq("sess-1"), anyMap())).thenReturn(node("""
            {"status":"success","result":{"approved":true,"message":"Ana Pérez","request_id":"req-7","authorization_devices":[]}}"""));
        when(client.confirmTransfer(eq("sess-1"), anyMap())).thenReturn(node("{\"status\":\"error\",\"message\":\"Respuesta inesperada del banco\"}"));

        BankStep accounts = service.login(1L, request(null, Map.of("username", "12345", "password", "gfdsa")));
        assertThatThrownBy(() -> service.transfer(2L, new BankStepRequest(accounts.session(), null, null, null, null, "a1", null, null, null)))
            .hasMessageContaining("Empezá de nuevo");
        service.transfer(1L, new BankStepRequest(accounts.session(), null, null, null, null, "a1", null, null, null));

        assertThatThrownBy(() -> service.confirm(1L, new BankStepRequest(accounts.session(), null, null, null, null, null, "", "", null)))
            .hasMessageContaining("No se debitó nada");
        verify(paymentService, never()).markPaidByBankTransfer(any(), any());
        verify(client).logout("sess-1");
    }

    @Test
    void notAvailableWithoutTheProfessionalsBankDetails() {
        TeacherProfile incomplete = new TeacherProfile();
        incomplete.setTransferBankName("BROU");
        when(teacherProfileRepository.findById(9L)).thenReturn(Optional.of(incomplete));

        assertThat(service.options(1L).available()).isFalse();
        assertThatThrownBy(() -> service.login(1L, request(null, Map.of("username", "1", "password", "2"))))
            .hasMessageContaining("datos bancarios");
    }

    @Test
    void bankNamesAreNormalizedForMatching() {
        assertThat(BankTransferPaymentService.normalizeBank("Banco Itaú Uruguay S.A.")).isEqualTo("itau");
        assertThat(BankTransferPaymentService.normalizeBank("Banco de la República Oriental del Uruguay")).isEqualTo("brou");
    }

    private BankStepRequest request(String session, Map<String, String> fields) {
        return new BankStepRequest(session, "test", fields, null, null, null, null, null, null);
    }

    private JsonNode node(String raw) throws Exception {
        return json.readTree(raw);
    }
}
