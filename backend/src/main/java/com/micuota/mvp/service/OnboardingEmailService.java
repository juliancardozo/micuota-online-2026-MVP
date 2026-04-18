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

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

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
                        <a href="%s/landing.html#acceso" style="display:inline-block;padding:12px 18px;border-radius:999px;background:#cc5a16;color:#fff8f0;text-decoration:none;font-weight:700;">Empezar ahora</a>
                      </p>
                      <p style="margin:0;font-size:12px;color:#7b705f;">Si no solicitaste este registro, ignora este mensaje.</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(fullName, appBaseUrl);

        send(lead.email(), "MiCuota | Comenzo tu onboarding de cobros", html);
    }

    public void sendNewTenantWelcomeEmail(String toEmail, String fullName, String tenantName, String tenantSlug, String dashboardUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        String safeName = fullName == null || fullName.isBlank() ? "Hola" : escapeHtml(fullName);
        String safeTenantName = tenantName == null || tenantName.isBlank() ? "Tu tenant" : escapeHtml(tenantName);
        String safeTenantSlug = tenantSlug == null ? "" : escapeHtml(tenantSlug);
        String safeDashboardUrl = dashboardUrl == null || dashboardUrl.isBlank() ? appBaseUrl + "/backoffice.html" : escapeHtml(dashboardUrl);

        String html = """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Bienvenida MiCuota</title>
              </head>
              <body style="margin:0;padding:24px;background:#f3f7ff;font-family:Arial,sans-serif;color:#1f2937;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:16px;border:1px solid #dbe7ff;overflow:hidden;">
                  <tr>
                    <td style="padding:24px;background:linear-gradient(135deg,#0f3d6e,#0b7ec2);color:#ffffff;">
                      <h1 style="margin:0;font-size:24px;line-height:1.2;">Tu tenant ya esta listo</h1>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:24px;">
                      <p style="margin:0 0 12px;">%s, tu cuenta fue creada correctamente.</p>
                      <p style="margin:0 0 16px;color:#4b5563;">Estos son los datos de tu tenant:</p>
                      <ul style="margin:0 0 16px;padding-left:20px;line-height:1.7;">
                        <li><strong>Tenant:</strong> %s</li>
                        <li><strong>Slug:</strong> %s</li>
                        <li><strong>Email de acceso:</strong> %s</li>
                      </ul>
                      <p style="margin:0 0 18px;">
                        <a href="%s" style="display:inline-block;padding:12px 18px;border-radius:999px;background:#0f3d6e;color:#ffffff;text-decoration:none;font-weight:700;">Entrar a mi panel</a>
                      </p>
                      <p style="margin:0;font-size:12px;color:#6b7280;">Siguiente paso recomendado: crear tu primer curso y compartir tu primer link de cobro.</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(safeName, safeTenantName, safeTenantSlug, escapeHtml(toEmail), safeDashboardUrl);

        send(toEmail, "MiCuota | Tenant creado correctamente", html);
    }

    private void send(String to, String subject, String html) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception ex) {
            log.warn("No se pudo enviar email onboarding a {}: {}", to, ex.getMessage());
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
