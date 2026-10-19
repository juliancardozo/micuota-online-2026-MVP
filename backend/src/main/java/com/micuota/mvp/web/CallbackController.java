package com.micuota.mvp.web;

import com.micuota.mvp.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Retorno del navegador desde el checkout del proveedor.
 * Solo redirige a pago.html: el estado del pago lo define exclusivamente el webhook verificado.
 */
@RestController
@RequestMapping("/api/callbacks")
@Tag(name = "Callbacks", description = "Retorno del checkout del proveedor hacia la pagina de pago (no modifica estados)")
public class CallbackController {

    private final PaymentService paymentService;

    @Value("${app.frontend-base-url:${app.base-url}}")
    private String frontendBaseUrl;

    public CallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping({"/success", "/pending", "/failure", "/failed"})
    @Operation(summary = "Retorno por operationId", description = "Redirige a la pagina de pago. No modifica el estado.")
    public RedirectView returnByOperationId(@RequestParam Long operationId) {
        return redirectToPayment(operationId);
    }

    @GetMapping({"/success/by-reference", "/pending/by-reference", "/failure/by-reference", "/failed/by-reference"})
    @Operation(summary = "Retorno por referencia", description = "Redirige a la pagina de pago. No modifica el estado.")
    public RedirectView returnByReference(
        @RequestParam(required = false) String providerReference,
        @RequestParam(name = "preference_id", required = false) String preferenceId,
        @RequestParam(required = false) String externalReference,
        @RequestParam(name = "external_reference", required = false) String mercadoPagoExternalReference
    ) {
        Long operationId = paymentService.findOperationIdByCallbackReference(
            firstNonBlank(providerReference, preferenceId),
            firstNonBlank(externalReference, mercadoPagoExternalReference)
        ).orElse(null);
        return redirectToPayment(operationId);
    }

    private RedirectView redirectToPayment(Long operationId) {
        UriComponentsBuilder target = UriComponentsBuilder
            .fromUriString(frontendBaseUrl)
            .path("/pago.html");
        if (operationId != null) {
            target.queryParam("operationId", operationId);
        }
        RedirectView redirectView = new RedirectView(target.build().toUriString());
        redirectView.setExposeModelAttributes(false);
        return redirectView;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
