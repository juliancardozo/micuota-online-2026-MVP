package com.micuota.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.repository.TeacherProfileRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conecta la cuenta de Mercado Pago de cada profesor a la aplicacion marketplace de MiCuota.
 * Con el token OAuth resultante los cobros pueden llevar marketplace_fee, que Mercado Pago
 * transfiere a la cuenta duena de la aplicacion (MiCuota).
 */
@Service
public class MercadoPagoOAuthService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoOAuthService.class);
    private static final long STATE_TTL_SECONDS = 15 * 60;
    private static final long REFRESH_WINDOW_DAYS = 30;

    private final MercadoPagoService mercadoPagoService;
    private final TeacherProfileRepository teacherProfileRepository;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authorizationUrl;
    private final String stateSecret;
    private final PlatformSettingsService platformSettingsService;

    public MercadoPagoOAuthService(
        MercadoPagoService mercadoPagoService,
        TeacherProfileRepository teacherProfileRepository,
        ObjectMapper objectMapper,
        @Value("${app.payments.mercadopago.oauth.client-id:}") String clientId,
        @Value("${app.payments.mercadopago.oauth.client-secret:}") String clientSecret,
        @Value("${app.payments.mercadopago.oauth.redirect-uri:}") String redirectUri,
        @Value("${app.payments.mercadopago.oauth.authorization-url:https://auth.mercadopago.com/authorization}") String authorizationUrl,
        @Value("${app.auth.session-secret:micuota-local-dev-secret-change-in-prod}") String stateSecret,
        PlatformSettingsService platformSettingsService
    ) {
        this.mercadoPagoService = mercadoPagoService;
        this.teacherProfileRepository = teacherProfileRepository;
        this.objectMapper = objectMapper;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.authorizationUrl = authorizationUrl;
        this.stateSecret = stateSecret;
        this.platformSettingsService = platformSettingsService;
    }

    /** Hay credenciales de la aplicacion marketplace (variables de entorno). */
    public boolean hasCredentials() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    /** Con credenciales y con el interruptor del admin prendido. Apagado, se conecta pegando el Access Token. */
    public boolean isEnabled() {
        return hasCredentials() && platformSettingsService.mercadoPagoOAuthOn();
    }

    public String authorizationUrlFor(Long teacherProfileId) {
        requireEnabled();
        return authorizationUrl
            + "?client_id=" + encode(clientId)
            + "&response_type=code"
            + "&platform_id=mp"
            + "&state=" + encode(signState(teacherProfileId))
            + "&redirect_uri=" + encode(redirectUri);
    }

    @Transactional
    public void completeAuthorization(String code, String state) {
        requireEnabled();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago no devolvio codigo de autorizacion");
        }
        Long teacherProfileId = verifyState(state);
        TeacherProfile profile = teacherProfileRepository.findById(teacherProfileId)
            .orElseThrow(() -> new IllegalArgumentException("Profesor no encontrado"));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("client_id", clientId);
        payload.put("client_secret", clientSecret);
        payload.put("grant_type", "authorization_code");
        payload.put("code", code);
        payload.put("redirect_uri", redirectUri);
        applyTokens(profile, mercadoPagoService.oauthToken(payload).body());
        teacherProfileRepository.save(profile);
        log.info("Mercado Pago conectado por OAuth teacherProfileId={} mpUserId={}", profile.getId(), profile.getMpUserId());
    }

    @Transactional
    public void disconnect(Long teacherProfileId) {
        TeacherProfile profile = teacherProfileRepository.findById(teacherProfileId)
            .orElseThrow(() -> new IllegalArgumentException("Profesor no encontrado"));
        clearConnection(profile);
        teacherProfileRepository.save(profile);
    }

    public static void clearConnection(TeacherProfile profile) {
        profile.setMpAccessToken(null);
        profile.setMpPublicKey(null);
        profile.setMpOauthConnected(false);
        profile.setMpRefreshToken(null);
        profile.setMpUserId(null);
        profile.setMpTokenExpiresAt(null);
    }

    // Los tokens OAuth de Mercado Pago duran 180 dias: se renuevan con un mes de margen.
    @Scheduled(cron = "${app.payments.mercadopago.oauth.refresh-cron:0 15 3 * * *}")
    public void refreshExpiringTokens() {
        if (!hasCredentials()) {
            return;
        }
        OffsetDateTime limit = OffsetDateTime.now(ZoneOffset.UTC).plusDays(REFRESH_WINDOW_DAYS);
        List<TeacherProfile> expiring = teacherProfileRepository.findByMpOauthConnectedTrueAndMpTokenExpiresAtBefore(limit);
        for (TeacherProfile profile : expiring) {
            try {
                refresh(profile);
            } catch (RuntimeException exception) {
                log.warn("No se pudo renovar el token OAuth de Mercado Pago teacherProfileId={}: {}", profile.getId(), exception.getMessage());
            }
        }
    }

    public void refresh(TeacherProfile profile) {
        if (profile.getMpRefreshToken() == null || profile.getMpRefreshToken().isBlank()) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("client_id", clientId);
        payload.put("client_secret", clientSecret);
        payload.put("grant_type", "refresh_token");
        payload.put("refresh_token", profile.getMpRefreshToken());
        applyTokens(profile, mercadoPagoService.oauthToken(payload).body());
        teacherProfileRepository.save(profile);
    }

    private void applyTokens(TeacherProfile profile, JsonNode body) {
        String accessToken = body.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Mercado Pago no devolvio access_token");
        }
        profile.setMpAccessToken(accessToken);
        profile.setMpOauthConnected(true);
        String refreshToken = body.path("refresh_token").asText("");
        if (!refreshToken.isBlank()) {
            profile.setMpRefreshToken(refreshToken);
        }
        String publicKey = body.path("public_key").asText("");
        if (!publicKey.isBlank()) {
            profile.setMpPublicKey(publicKey);
        }
        String userId = body.path("user_id").asText("");
        if (!userId.isBlank()) {
            profile.setMpUserId(userId);
        }
        long expiresIn = body.path("expires_in").asLong(0);
        profile.setMpTokenExpiresAt(expiresIn > 0 ? OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(expiresIn) : null);
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("La conexion con Mercado Pago por OAuth no esta configurada");
        }
    }

    private String signState(Long teacherProfileId) {
        long expiresAt = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() + STATE_TTL_SECONDS;
        String payload = teacherProfileId + ":" + expiresAt + ":" + UUID.randomUUID();
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return payloadB64 + "." + hmac(payloadB64);
    }

    private Long verifyState(String state) {
        String[] parts = state == null ? new String[0] : state.split("\\.");
        if (parts.length != 2 || !MessageDigest.isEqual(
            hmac(parts[0]).getBytes(StandardCharsets.UTF_8),
            parts[1].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("Estado OAuth invalido");
        }
        String[] values = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":");
        if (values.length != 3 || OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond() > Long.parseLong(values[1])) {
            throw new IllegalArgumentException("La autorizacion de Mercado Pago vencio. Intenta de nuevo.");
        }
        return Long.parseLong(values[0]);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(("mp-oauth-state:" + stateSecret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo firmar el estado OAuth", exception);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
