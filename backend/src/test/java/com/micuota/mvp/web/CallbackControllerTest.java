package com.micuota.mvp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.micuota.mvp.service.PaymentService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CallbackControllerTest {

    @Mock
    private PaymentService paymentService;

    private CallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new CallbackController(paymentService);
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "https://micuota.online");
    }

    @Test
    void returnByOperationIdOnlyRedirects() {
        assertThat(controller.returnByOperationId(42L).getUrl())
            .isEqualTo("https://micuota.online/pago.html?operationId=42");
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    void returnByReferenceResolvesOperationWithoutChangingStatus() {
        when(paymentService.findOperationIdByCallbackReference(null, "MC-abc")).thenReturn(Optional.of(7L));

        assertThat(controller.returnByReference(null, null, "MC-abc", null).getUrl())
            .isEqualTo("https://micuota.online/pago.html?operationId=7");
        verify(paymentService).findOperationIdByCallbackReference(null, "MC-abc");
        verifyNoMoreInteractions(paymentService);
    }
}
