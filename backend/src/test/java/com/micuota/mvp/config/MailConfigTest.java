package com.micuota.mvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;

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
}
