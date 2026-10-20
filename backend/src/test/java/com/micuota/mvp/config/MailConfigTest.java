package com.micuota.mvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

class MailConfigTest {

    @Test
    void explicitResendKeyWins() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.resend.com");
        properties.setPassword("re_from_password");

        assertThat(MailConfig.resolveResendKey(properties, "re_explicit")).isEqualTo("re_explicit");
    }

    @Test
    void resendSmtpPasswordIsUsedAsApiKey() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.resend.com");
        properties.setPassword("re_from_password");

        assertThat(MailConfig.resolveResendKey(properties, "")).isEqualTo("re_from_password");
    }

    @Test
    void otherSmtpHostsKeepUsingSmtp() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.gmail.com");
        properties.setPassword("app-password");

        assertThat(MailConfig.resolveResendKey(properties, null)).isEmpty();
    }

    @Test
    void htmlMessageBuiltLikeOurServicesIsSentAsHtml() throws Exception {
        // Mismo armado que OnboardingEmailService: MimeMessageHelper sin multipart y setText(html, true).
        var mime = new JavaMailSenderImpl().createMimeMessage();
        var helper = new MimeMessageHelper(mime, "UTF-8");
        helper.setFrom("no-reply@micuota.online");
        helper.setTo("ana@example.com");
        helper.setSubject("123456 es tu código de MiCuota");
        helper.setText("<p>Tu código es <strong>123456</strong></p>", true);

        Map<String, Object> payload = MailConfig.ResendAwareMailSender.toResendPayload(mime);

        assertThat(payload)
            .containsEntry("from", "no-reply@micuota.online")
            .containsEntry("to", List.of("ana@example.com"))
            .containsEntry("subject", "123456 es tu código de MiCuota")
            .containsEntry("html", "<p>Tu código es <strong>123456</strong></p>")
            .doesNotContainKey("text");
    }
}
