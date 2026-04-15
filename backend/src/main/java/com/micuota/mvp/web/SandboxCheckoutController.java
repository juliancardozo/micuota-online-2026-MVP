package com.micuota.mvp.web;

import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.repository.PaymentOperationRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sandbox")
public class SandboxCheckoutController {

    private final PaymentOperationRepository paymentOperationRepository;

    public SandboxCheckoutController(
    PaymentOperationRepository paymentOperationRepository
    ) {
        this.paymentOperationRepository = paymentOperationRepository;
    }

    @GetMapping(value = "/{provider}/checkout/{providerReference}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> sandboxCheckout(
        @PathVariable String provider,
    @PathVariable String providerReference
    ) {
        PaymentOperation operation = paymentOperationRepository.findByProviderReference(providerReference)
            .orElse(null);

        if (operation == null) {
            return ResponseEntity.status(404).body(renderNotFound(provider, providerReference));
        }

        return ResponseEntity.ok(renderCheckout(provider, providerReference, operation));
    }

    private String renderNotFound(String provider, String providerReference) {
        return """
            <!doctype html>
            <html lang=\"es\">
              <head>
                <meta charset=\"UTF-8\" />
                <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                <title>Sandbox no encontrado</title>
                <style>
                  body { font-family: Arial, sans-serif; margin: 32px; background: #f8fafc; color: #1f2937; }
                  .card { max-width: 760px; margin: 0 auto; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; }
                  h1 { margin-top: 0; }
                  code { background: #f3f4f6; padding: 2px 6px; border-radius: 6px; }
                </style>
              </head>
              <body>
                <section class=\"card\">
                  <h1>Operacion no encontrada</h1>
                  <p>No existe una operacion para provider/ref:</p>
                  <p><code>%s / %s</code></p>
                </section>
              </body>
            </html>
            """.formatted(provider, providerReference);
    }

    private String renderCheckout(String provider, String providerReference, PaymentOperation operation) {
        String operationUrl = "/api/public/payments/" + operation.getId();
        String appPaymentUrl = "/pago.html?operationId=" + operation.getId();
        String successHookUrl = "/api/callbacks/success?operationId=" + operation.getId();
        String pendingHookUrl = "/api/callbacks/pending?operationId=" + operation.getId();
        String failureHookUrl = "/api/callbacks/failure?operationId=" + operation.getId();

        return """
            <!doctype html>
            <html lang=\"es\">
              <head>
                <meta charset=\"UTF-8\" />
                <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                <title>Sandbox %s</title>
                <style>
                  body { font-family: Arial, sans-serif; margin: 0; background: #f4f8ff; color: #10243f; }
                  .wrap { max-width: 860px; margin: 24px auto; padding: 0 16px; }
                  .card { background: #fff; border: 1px solid #dbe6f3; border-radius: 14px; padding: 20px; }
                  h1 { margin: 0 0 8px; font-size: 28px; }
                  p { margin: 8px 0; }
                  .meta { margin-top: 12px; padding: 12px; border-radius: 10px; background: #f7fbff; border: 1px solid #dbe6f3; }
                  .status { display: inline-block; margin-top: 8px; font-weight: 700; }
                  .actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }
                  .btn { text-decoration: none; border-radius: 999px; padding: 10px 14px; border: 1px solid #c6d7ea; background: #fff; color: #0f3d6e; font-weight: 700; cursor: pointer; }
                  .primary { background: #0f3d6e; border-color: #0f3d6e; color: #fff; }
                  .hint { font-size: 13px; color: #415a77; margin-top: 10px; }
                </style>
              </head>
              <body>
                <div class=\"wrap\">
                  <section class=\"card\">
                    <h1>Sandbox %s checkout</h1>
                    <p>Referencia: <strong>%s</strong></p>
                    <div class=\"meta\">
                      <p>Operacion: <strong>#%d</strong></p>
                      <p>Descripcion: %s</p>
                      <p>Monto: <strong>%s %s</strong></p>
                      <p class=\"status\">Estado actual: <span id=\"status-value\">%s</span></p>
                    </div>

                    <div class=\"actions\">
                      <button class=\"btn primary\" type=\"button\" onclick=\"simulate('%s')\">Simular pago exitoso</button>
                      <button class=\"btn\" type=\"button\" onclick=\"simulate('%s')\">Simular pendiente</button>
                      <button class=\"btn\" type=\"button\" onclick=\"simulate('%s')\">Simular fallo</button>
                    </div>
                    <p class=\"hint\">Los botones llaman a los hooks reales del backend en <code>/api/callbacks/*</code>.</p>

                    <div class=\"actions\" style=\"margin-top: 12px;\">
                      <a class=\"btn\" href=\"%s\" target=\"_blank\" rel=\"noopener noreferrer\">Ver API publica</a>
                      <a class=\"btn\" href=\"%s\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir pago en app</a>
                    </div>

                    <script>
                      async function refreshStatus() {
                        const response = await fetch('%s');
                        if (!response.ok) return;
                        const data = await response.json();
                        const statusNode = document.getElementById('status-value');
                        if (statusNode && data && data.status) {
                          statusNode.textContent = data.status;
                        }
                      }

                      async function simulate(hookUrl) {
                        try {
                          const response = await fetch(hookUrl);
                          if (!response.ok) {
                            alert('No se pudo actualizar el estado via hook.');
                            return;
                          }
                          await refreshStatus();
                        } catch (_error) {
                          alert('No se pudo conectar con el backend para simular el estado.');
                        }
                      }
                    </script>
                  </section>
                </div>
              </body>
            </html>
            """.formatted(
            provider,
            provider,
            providerReference,
            operation.getId(),
            operation.getDescription(),
            operation.getCurrency(),
            operation.getAmount(),
            operation.getStatus(),
            successHookUrl,
            pendingHookUrl,
            failureHookUrl,
            operationUrl,
            appPaymentUrl,
            operationUrl
        );
    }
}
