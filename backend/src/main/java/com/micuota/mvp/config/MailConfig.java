package com.micuota.mvp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Render bloquea el SMTP saliente en el plan gratis, asi que con Resend mandamos por su API HTTPS.
 * Si no hay clave de Resend, se usa SMTP comun con la configuracion spring.mail.*.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    JavaMailSender javaMailSender(
        MailProperties properties,
        ObjectMapper objectMapper,
        @Value("${app.mail.resend-api-key:}") String resendApiKey
    ) {
        ResendAwareMailSender sender = new ResendAwareMailSender(resolveResendKey(properties, resendApiKey), objectMapper);
        sender.setHost(properties.getHost());
        if (properties.getPort() != null) {
            sender.setPort(properties.getPort());
        }
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setProtocol(properties.getProtocol());
        if (properties.getDefaultEncoding() != null) {
            sender.setDefaultEncoding(properties.getDefaultEncoding().name());
        }
        sender.getJavaMailProperties().putAll(properties.getProperties());
        return sender;
    }

    // Con MAIL_HOST=smtp.resend.com, MAIL_PASSWORD ya es la API key: no hace falta otra variable.
    static String resolveResendKey(MailProperties properties, String resendApiKey) {
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            return resendApiKey.trim();
        }
        if ("smtp.resend.com".equalsIgnoreCase(properties.getHost()) && properties.getPassword() != null && !properties.getPassword().isBlank()) {
            return properties.getPassword().trim();
        }
        return "";
    }

    static class ResendAwareMailSender extends JavaMailSenderImpl {

        private static final URI RESEND_EMAILS = URI.create("https://api.resend.com/emails");

        private final String resendApiKey;
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        ResendAwareMailSender(String resendApiKey, ObjectMapper objectMapper) {
            this.resendApiKey = resendApiKey;
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            if (resendApiKey.isBlank()) {
                super.doSend(mimeMessages, originalMessages);
                return;
            }
            for (MimeMessage message : mimeMessages) {
                sendWithResend(message);
            }
        }

        private void sendWithResend(MimeMessage message) {
            try {
                Map<String, Object> body = toResendPayload(message);
                HttpRequest request = HttpRequest.newBuilder(RESEND_EMAILS)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new MailSendException("Resend respondio " + response.statusCode() + ": " + response.body());
                }
            } catch (MailSendException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new MailSendException("Envio a Resend interrumpido", ex);
            } catch (Exception ex) {
                throw new MailSendException("No se pudo enviar con Resend: " + ex.getMessage(), ex);
            }
        }

        static Map<String, Object> toResendPayload(MimeMessage message) throws Exception {
            // Como hace JavaMailSenderImpl antes de SMTP: fija el Content-Type real (text/html) de cada parte.
            message.saveChanges();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", addresses(message.getFrom()).get(0));
            body.put("to", addresses(message.getRecipients(Message.RecipientType.TO)));
            List<String> cc = addresses(message.getRecipients(Message.RecipientType.CC));
            if (!cc.isEmpty()) {
                body.put("cc", cc);
            }
            body.put("subject", message.getSubject());
            String html = findBody(message, "text/html");
            if (html != null) {
                body.put("html", html);
            } else {
                body.put("text", findBody(message, "text/plain"));
            }
            return body;
        }

        private static List<String> addresses(Address[] addresses) {
            return addresses == null ? List.of() : Arrays.stream(addresses).map(Address::toString).toList();
        }

        private static String findBody(Part part, String mimeType) throws Exception {
            if (part.isMimeType(mimeType) && part.getContent() instanceof String text) {
                return text;
            }
            if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    String found = findBody(multipart.getBodyPart(i), mimeType);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
    }
}
