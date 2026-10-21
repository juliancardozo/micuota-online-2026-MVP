package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MercadoPagoAccountGuardTest {

    private final ObjectMapper json = new ObjectMapper();

    @Mock
    private MercadoPagoService mercadoPagoService;
    @Mock
    private PlatformSettingsService settings;

    private MercadoPagoAccountGuard guard;

    @BeforeEach
    void setUp() {
        guard = new MercadoPagoAccountGuard(mercadoPagoService, settings);
    }

    @Test
    void liveModeDoesNotCheckAnything() {
        when(settings.isLive()).thenReturn(true);
        assertThatCode(() -> guard.requireAllowed("APP_USR-real")).doesNotThrowAnyException();
        verify(mercadoPagoService, never()).getCurrentUser(anyString());
    }

    @Test
    void testModeAcceptsTestUsersAndRemembersTheAnswer() throws Exception {
        when(settings.isLive()).thenReturn(false);
        when(mercadoPagoService.getCurrentUser("APP_USR-test")).thenReturn(me("{\"nickname\":\"TESTUSER2374051608077138579\",\"tags\":[\"normal\",\"test_user\"]}"));

        assertThatCode(() -> guard.requireAllowed("APP_USR-test")).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAllowed("APP_USR-test")).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAllowed("TEST-legacy-token")).doesNotThrowAnyException();
        verify(mercadoPagoService, times(1)).getCurrentUser("APP_USR-test");
    }

    @Test
    void testModeBlocksRealAccountsAndUnverifiableOnes() throws Exception {
        when(settings.isLive()).thenReturn(false);
        when(mercadoPagoService.getCurrentUser("APP_USR-real")).thenReturn(me("{\"nickname\":\"MICUOTAJULIAN\",\"tags\":[\"normal\"]}"));
        when(mercadoPagoService.getCurrentUser("APP_USR-down")).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> guard.requireAllowed("APP_USR-real"))
            .isInstanceOf(TestModeViolationException.class)
            .hasMessageContaining("modo prueba");
        assertThatThrownBy(() -> guard.requireAllowed("APP_USR-down"))
            .isInstanceOf(TestModeViolationException.class)
            .hasMessageContaining("no pudimos verificar");
    }

    private MercadoPagoService.MercadoPagoApiResponse me(String raw) throws Exception {
        JsonNode node = json.readTree(raw);
        return new MercadoPagoService.MercadoPagoApiResponse(raw, node);
    }
}
