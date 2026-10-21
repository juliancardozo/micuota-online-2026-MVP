package com.micuota.mvp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OnboardingEmailService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@micuota.online}")
    private String fromEmail;

    @Value("${app.frontend-base-url:${app.base-url:http://localhost:8080}}")
    private String frontendBaseUrl;

    public OnboardingEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendNewLeadCampaignEmail(LeadView lead) {
        if (lead == null || lead.email() == null || lead.email().isBlank()) {
            return;
        }

        String fullName = lead.fullName() == null || lead.fullName().isBlank() ? "Hola" : escapeHtml(lead.fullName());

        String html = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Onboarding MiCuota</title>
              </head>
              <body style="margin:0;padding:24px;background:#f8f3ea;font-family:Arial,sans-serif;color:#241f19;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;margin:0 auto;background:#fffaf2;border-radius:16px;border:1px solid #ead9bd;overflow:hidden;">
                  <tr>
                    <td style="padding:22px;background:linear-gradient(135deg,#cc5a16,#f0a64a);color:#fff8f0;">
                      <h1 style="margin:0;font-size:24px;line-height:1.2;">Tu onboarding en MiCuota ya comenzo</h1>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:22px;">
                      <p style="margin:0 0 12px;">%s, gracias por sumarte a la lista de interesados.</p>
                      <p style="margin:0 0 16px;color:#5f5648;">Objetivo: ayudarte a cobrar sin perseguir a nadie y con menos trabajo manual.</p>
                      <ol style="margin:0 0 16px;padding-left:20px;color:#2f291f;line-height:1.6;">
                        <li>Dia 1: como crear tu primer cobro en menos de 5 minutos.</li>
                        <li>Dia 2: como ver pendientes por persona y reducir mensajes incomodos.</li>
                        <li>Dia 3: como ordenar cobros semanales y mensuales sin planillas.</li>
                      </ol>
                      <p style="margin:0 0 18px;">
                        <a href="%s/cuenta.html#crear" style="display:inline-block;padding:12px 18px;border-radius:999px;background:#cc5a16;color:#fff8f0;text-decoration:none;font-weight:700;">Empezar ahora</a>
                      </p>
                      <p style="margin:0;font-size:12px;color:#7b705f;">Si no solicitaste este registro, ignora este mensaje.</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(fullName, frontendBaseUrl);

        send(lead.email(), "MiCuota | Comenzo tu onboarding de cobros", html);
    }

    /** Codigo de 6 digitos para entrar o crear cuenta. A diferencia del resto, si falla el envio se avisa al usuario. */
    public void sendLoginCode(String toEmail, String code, int ttlMinutes) {
        String html = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Tu código de MiCuota</title>
              </head>
              <body style="margin:0;padding:24px;background:#f4f8f6;font-family:Arial,sans-serif;color:#1f2a24;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;border:1px solid #d7e7df;">
                  <tr>
                    <td style="padding:28px;">
                      <p style="margin:0 0 8px;font-size:16px;">Tu código para entrar a MiCuota es:</p>
                      <p style="margin:0 0 16px;font-size:36px;font-weight:700;letter-spacing:8px;color:#0b6b4d;">%s</p>
                      <p style="margin:0 0 8px;color:#4b5a52;">Vence en %d minutos. Escribilo en la pantalla donde lo pediste.</p>
                      <p style="margin:0;font-size:12px;color:#6b7a72;">Si no lo pediste vos, ignorá este mensaje: nadie puede entrar sin este código.</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(code, ttlMinutes);

        sendOrThrow(toEmail, code + " es tu código de MiCuota", html);
    }

    public void sendNewTenantWelcomeEmail(String toEmail, String fullName, String tenantName, String tenantSlug) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        String firstName = fullName == null || fullName.isBlank() ? "Hola" : escapeHtml(fullName.trim().split("\\s+")[0]);
        String entrarUrl = escapeHtml(frontendBaseUrl + "/cuenta.html#entrar");

        String html = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Bienvenida MiCuota</title>
              </head>
              <body style="margin:0;padding:24px;background:#f4f8f6;font-family:Arial,sans-serif;color:#1f2a24;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;border:1px solid #d7e7df;">
                  <tr>
                    <td style="padding:28px;">
                      <h1 style="margin:0 0 12px;font-size:24px;line-height:1.2;">%s, tu cuenta ya está lista</h1>
                      <p style="margin:0 0 16px;color:#4b5a52;">Para entrar no necesitás contraseña: ponés tu email (%s) y te mandamos un código.</p>
                      <ol style="margin:0 0 20px;padding-left:20px;line-height:1.7;">
                        <li>Elegí cómo te pagan: Mercado Pago, transferencia, giro o efectivo.</li>
                        <li>Creá tu primer cobro, por ejemplo "Cuota de noviembre".</li>
                        <li>Mandalo por WhatsApp. Si pagan con Mercado Pago se marca solo; si no, lo marcás con un toque.</li>
                      </ol>
                      <p style="margin:0 0 18px;">
                        <a href="%s" style="display:inline-block;padding:12px 18px;border-radius:999px;background:#0b6b4d;color:#ffffff;text-decoration:none;font-weight:700;">Entrar a MiCuota</a>
                      </p>
                      <p style="margin:0;font-size:12px;color:#6b7a72;">Código de tu cuenta: %s</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(firstName, escapeHtml(toEmail), entrarUrl, escapeHtml(tenantSlug == null ? "" : tenantSlug));

        send(toEmail, "Bienvenida a MiCuota", html);
    }

    /** Invitacion a sumarse a una cuenta: profesional del equipo o administrador de la academia. */
    public void sendTeamInviteEmail(String toEmail, String fullName, String tenantName, boolean academyAdmin) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        String firstName = fullName == null || fullName.isBlank() ? "Hola" : escapeHtml(fullName.trim().split("\\s+")[0]);
        String entrarUrl = escapeHtml(frontendBaseUrl + "/cuenta.html#entrar");
        String steps = academyAdmin
            ? """
                <li>Sumá a los profes de tu equipo.</li>
                <li>Cada profe conecta su Mercado Pago: la plata va directo a su cuenta.</li>
                <li>Creá cobros y seguí quién pagó desde un solo lugar.</li>
                """
            : """
                <li>Conectá tu Mercado Pago: ahí te llega la plata.</li>
                <li>Creá tus cobros y mandalos por WhatsApp.</li>
                <li>Te avisamos cuando te paguen.</li>
                """;

        String html = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Te sumaron a MiCuota</title>
              </head>
              <body style="margin:0;padding:24px;background:#f4f8f6;font-family:Arial,sans-serif;color:#1f2a24;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;border:1px solid #d7e7df;">
                  <tr>
                    <td style="padding:28px;">
                      <h1 style="margin:0 0 12px;font-size:24px;line-height:1.2;">%s, te sumaron a %s</h1>
                      <p style="margin:0 0 16px;color:#4b5a52;">Para entrar no necesitás contraseña: ponés tu email (%s) y te mandamos un código.</p>
                      <ol style="margin:0 0 20px;padding-left:20px;line-height:1.7;">%s</ol>
                      <p style="margin:0;">
                        <a href="%s" style="display:inline-block;padding:12px 18px;border-radius:999px;background:#0b6b4d;color:#ffffff;text-decoration:none;font-weight:700;">Entrar a MiCuota</a>
                      </p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(firstName, escapeHtml(tenantName == null ? "MiCuota" : tenantName), escapeHtml(toEmail), steps, entrarUrl);

        send(toEmail, "Te sumaron a " + (tenantName == null ? "MiCuota" : tenantName), html);
    }

    private void send(String to, String subject, String html) {
        try {
            sendOrThrow(to, subject, html);
        } catch (IllegalStateException ex) {
            log.warn("No se pudo enviar email onboarding a {}: {}", to, ex.getMessage());
        }
    }

    private void sendOrThrow(String to, String subject, String html) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception ex) {
            throw new IllegalStateException("Fallo el envio de email: " + ex.getMessage(), ex);
        }
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
