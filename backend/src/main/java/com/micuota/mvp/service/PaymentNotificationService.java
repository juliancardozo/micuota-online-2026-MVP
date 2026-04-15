package com.micuota.mvp.service;

import com.micuota.mvp.domain.PaymentOperation;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@micuota.online}")
    private String fromEmail;

    public PaymentNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPaymentCreatedEmail(String payerEmail, String teacherName, PaymentOperation operation) {
        if (payerEmail == null || payerEmail.isBlank()) {
            return;
        }

        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(payerEmail);
            helper.setSubject("MiCuota | Nuevo link de pago disponible");
            helper.setText(buildHtmlTemplate(teacherName, operation), true);

            mailSender.send(mime);
        } catch (Exception ex) {
            log.warn("No se pudo enviar mail de pago a {}: {}", payerEmail, ex.getMessage());
        }
    }

    private String buildHtmlTemplate(String teacherName, PaymentOperation operation) {
        String displayTeacher = (teacherName == null || teacherName.isBlank()) ? "Tu profesional" : teacherName;
        String encodedCheckout = URLEncoder.encode(operation.getCheckoutUrl(), StandardCharsets.UTF_8);
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=" + encodedCheckout;

        return """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Nuevo pago MiCuota</title>
              </head>
              <body style="margin:0;padding:24px;background:#f3f7ff;font-family:Arial,sans-serif;color:#1f2937;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:16px;border:1px solid #dbe7ff;overflow:hidden;">
                  <tr>
                    <td style="padding:24px;background:linear-gradient(135deg,#0f3d6e,#0b7ec2);color:#ffffff;">
                      <h1 style="margin:0;font-size:24px;line-height:1.2;">Tienes un nuevo pago para completar</h1>
                      <p style="margin:8px 0 0;opacity:.92;">%s te compartio un cobro por MiCuota.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:24px;">
                      <p style="margin:0 0 12px;">Concepto: <strong>%s</strong></p>
                      <p style="margin:0 0 20px;">Monto: <strong>%s %s</strong></p>
                      <p style="margin:0 0 14px;">Puedes pagar con el boton o escanear el QR:</p>
                      <p style="margin:0 0 16px;">
                        <a href="%s" style="display:inline-block;padding:12px 18px;border-radius:999px;background:#0f3d6e;color:#ffffff;text-decoration:none;font-weight:700;">Ir al link de pago</a>
                      </p>
                      <p style="margin:0 0 8px;"><img src="%s" alt="QR de pago" width="220" height="220" style="display:block;border-radius:12px;border:1px solid #dbe7ff;" /></p>
                      <p style="margin:18px 0 0;font-size:12px;color:#6b7280;">Operacion #%d · Estado inicial: %s</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(
            escapeHtml(displayTeacher),
            escapeHtml(operation.getDescription()),
            operation.getCurrency(),
            operation.getAmount(),
            operation.getCheckoutUrl(),
            qrUrl,
            operation.getId(),
            operation.getStatus()
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
