package com.micuota.mvp.service;

import com.micuota.mvp.domain.EmailLoginCode;
import com.micuota.mvp.domain.User;
import com.micuota.mvp.domain.UserRole;
import com.micuota.mvp.repository.EmailLoginCodeRepository;
import com.micuota.mvp.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Entrar o crear cuenta con un codigo de 6 digitos enviado por email (sin contrasena).
 * El codigo prueba que la persona es duena del email: la cuenta se crea recien al verificarlo.
 */
@Service
public class EmailCodeAuthService {

    static final int CODE_TTL_MINUTES = 10;
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_CODES_PER_EMAIL_PER_HOUR = 5;
    static final int MAX_CODES_PER_IP_PER_HOUR = 20;
    static final int RESEND_COOLDOWN_SECONDS = 30;

    private static final Logger log = LoggerFactory.getLogger(EmailCodeAuthService.class);

    private final EmailLoginCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final TenantAuthService tenantAuthService;
    private final OnboardingEmailService onboardingEmailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.auth.session-secret:micuota-local-dev-secret-change-in-prod}")
    private String secret;

    // Solo para desarrollo local sin SMTP: escribe el codigo en el log.
    @Value("${app.auth.log-login-codes:false}")
    private boolean logLoginCodes;

    public EmailCodeAuthService(
        EmailLoginCodeRepository codeRepository,
        UserRepository userRepository,
        TenantAuthService tenantAuthService,
        OnboardingEmailService onboardingEmailService
    ) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.tenantAuthService = tenantAuthService;
        this.onboardingEmailService = onboardingEmailService;
    }

    public void requestCode(String rawEmail, String requestIp) {
        String email = normalizeEmail(rawEmail);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime hourAgo = now.minusHours(1);

        codeRepository.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            if (last.getCreatedAt().isAfter(now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
                throw new IllegalArgumentException("Ya te mandamos un código. Esperá unos segundos antes de pedir otro.");
            }
        });
        if (codeRepository.countByEmailAndCreatedAtAfter(email, hourAgo) >= MAX_CODES_PER_EMAIL_PER_HOUR
            || (requestIp != null && codeRepository.countByRequestIpAndCreatedAtAfter(requestIp, hourAgo) >= MAX_CODES_PER_IP_PER_HOUR)) {
            throw new IllegalArgumentException("Pediste muchos códigos seguidos. Probá de nuevo en un rato.");
        }

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        EmailLoginCode loginCode = new EmailLoginCode();
        loginCode.setEmail(email);
        loginCode.setCodeHash(hash(email, code));
        loginCode.setRequestIp(requestIp);
        loginCode.setCreatedAt(now);
        loginCode.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        loginCode = codeRepository.save(loginCode);

        if (logLoginCodes) {
            log.info("Codigo de acceso para {}: {}", email, code);
        }
        try {
            onboardingEmailService.sendLoginCode(email, code, CODE_TTL_MINUTES);
        } catch (IllegalStateException ex) {
            log.error("No se pudo enviar el codigo de acceso a {}: {}", email, ex.getMessage());
            codeRepository.delete(loginCode);
            throw new IllegalStateException("No pudimos mandarte el código. Revisá el email o probá de nuevo en un rato.");
        }
    }

    public EmailCodeVerifyResponse verify(EmailCodeVerifyRequest request) {
        String email = normalizeEmail(request.email());
        String code = request.code() == null ? "" : request.code().replaceAll("\\D", "");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EmailLoginCode loginCode = codeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
            .filter(c -> c.getExpiresAt().isAfter(now))
            .orElseThrow(() -> new IllegalArgumentException("El código venció. Pedí uno nuevo."));
        if (loginCode.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Probaste muchas veces. Pedí un código nuevo.");
        }
        if (!MessageDigest.isEqual(
            hash(email, code).getBytes(StandardCharsets.UTF_8),
            loginCode.getCodeHash().getBytes(StandardCharsets.UTF_8)
        )) {
            codeRepository.incrementAttempts(loginCode.getId());
            int left = MAX_ATTEMPTS - loginCode.getAttempts() - 1;
            throw new IllegalArgumentException(left > 0
                ? "Ese código no es. Revisalo e intentá de nuevo."
                : "Probaste muchas veces. Pedí un código nuevo.");
        }

        // Codigo correcto. Si falta un dato (nombre o cuenta), no se consume para que el mismo codigo sirva al reenviar.
        // El administrador de la plataforma no entra por codigo: solo con contraseña desde admin.html.
        List<User> allUsers = userRepository.findAllByEmailOrderByIdAsc(email);
        List<User> users = allUsers.stream().filter(u -> u.getRole() != UserRole.ADMIN).toList();
        if (users.isEmpty() && !allUsers.isEmpty()) {
            throw new IllegalArgumentException("Esta cuenta entra con contraseña.");
        }
        User target = null;
        if (users.size() == 1) {
            target = users.get(0);
        } else if (users.size() > 1) {
            String slug = request.tenantSlug() == null ? "" : request.tenantSlug().trim().toLowerCase(Locale.ROOT);
            target = users.stream().filter(u -> u.getTenant().getSlug().equals(slug)).findFirst().orElse(null);
            if (target == null) {
                return EmailCodeVerifyResponse.chooseAccount(users.stream()
                    .map(u -> new EmailCodeVerifyResponse.AccountOption(u.getTenant().getSlug(), u.getTenant().getName(), u.getRole()))
                    .toList());
            }
        } else if (request.fullName() == null || request.fullName().isBlank()) {
            return EmailCodeVerifyResponse.needsName();
        }

        if (codeRepository.consume(loginCode.getId(), now) == 0) {
            throw new IllegalArgumentException("Ese código ya se usó. Pedí uno nuevo.");
        }
        if (target != null) {
            return EmailCodeVerifyResponse.signedIn(tenantAuthService.signIn(target), false);
        }
        return EmailCodeVerifyResponse.signedIn(
            tenantAuthService.createSelfServiceTenant(request.fullName().trim(), email, blankToNull(request.phone()), request.activity()),
            true
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Falta el email");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("login-code:" + email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular el hash del codigo", ex);
        }
    }
}
