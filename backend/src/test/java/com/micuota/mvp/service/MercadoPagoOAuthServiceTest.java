package com.micuota.mvp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.repository.TeacherProfileRepository;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MercadoPagoOAuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MercadoPagoService mercadoPagoService;
    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    private MercadoPagoOAuthService service;

    @BeforeEach
    void setUp() {
        service = new MercadoPagoOAuthService(
            mercadoPagoService,
            teacherProfileRepository,
            objectMapper,
            "app-123",
            "secret-xyz",
            "https://api.micuota.online/api/mercadopago/oauth/callback",
            "https://auth.mercadopago.com/authorization",
            "test-secret"
        );
    }

    @Test
    void isDisabledWithoutClientCredentials() {
        MercadoPagoOAuthService disabled = new MercadoPagoOAuthService(
            mercadoPagoService, teacherProfileRepository, objectMapper, "", "", "https://x/callback", "https://auth", "s"
        );
        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.authorizationUrlFor(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeAuthorizationStoresOAuthTokensForTheTeacherInTheState() throws Exception {
        TeacherProfile profile = new TeacherProfile();
        profile.setMpAccessToken("APP_USR-manual");
        when(teacherProfileRepository.findById(7L)).thenReturn(Optional.of(profile));
        JsonNode tokens = objectMapper.readTree("""
            {"access_token":"APP_USR-oauth","refresh_token":"TG-refresh","public_key":"APP_USR-pk","user_id":987,"expires_in":15552000}
            """);
        when(mercadoPagoService.oauthToken(any(JsonNode.class)))
            .thenReturn(new MercadoPagoService.MercadoPagoApiResponse(tokens.toString(), tokens));

        Map<String, String> query = queryOf(service.authorizationUrlFor(7L));
        assertThat(query).containsEntry("client_id", "app-123").containsEntry("response_type", "code")
            .containsEntry("redirect_uri", "https://api.micuota.online/api/mercadopago/oauth/callback");

        service.completeAuthorization("TG-code", query.get("state"));

        ArgumentCaptor<JsonNode> payload = ArgumentCaptor.forClass(JsonNode.class);
        verify(mercadoPagoService).oauthToken(payload.capture());
        assertThat(payload.getValue().path("grant_type").asText()).isEqualTo("authorization_code");
        assertThat(payload.getValue().path("code").asText()).isEqualTo("TG-code");
        assertThat(profile.isMpOauthConnected()).isTrue();
        assertThat(profile.getMpAccessToken()).isEqualTo("APP_USR-oauth");
        assertThat(profile.getMpRefreshToken()).isEqualTo("TG-refresh");
        assertThat(profile.getMpUserId()).isEqualTo("987");
        assertThat(profile.getMpTokenExpiresAt()).isAfter(OffsetDateTime.now().plusDays(170));
        verify(teacherProfileRepository).save(profile);
    }

    @Test
    void tamperedStateIsRejectedBeforeCallingMercadoPago() {
        String state = queryOf(service.authorizationUrlFor(7L)).get("state");
        String forged = state.substring(0, state.indexOf('.')) + ".firma-falsa";

        assertThatThrownBy(() -> service.completeAuthorization("TG-code", forged))
            .hasMessageContaining("Estado OAuth invalido");
        verify(mercadoPagoService, never()).oauthToken(any());
    }

    private static Map<String, String> queryOf(String url) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
            .map(pair -> pair.split("=", 2))
            .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
