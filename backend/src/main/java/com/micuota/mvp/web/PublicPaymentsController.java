package com.micuota.mvp.web;

import com.micuota.mvp.service.PaymentPublicView;
import com.micuota.mvp.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/payments")
@Tag(name = "Public Payments", description = "Servicios publicos para la experiencia de pago del alumno/paciente")
public class PublicPaymentsController {

    private final PaymentService paymentService;

    public PublicPaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{operationId}")
    @Operation(summary = "Detalle publico de pago", description = "Obtiene una vista publica de la operacion para renderizar pago.html.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operacion encontrada"),
        @ApiResponse(responseCode = "400", description = "Operacion inexistente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaymentPublicView getPaymentForClient(@PathVariable Long operationId) {
        return paymentService.getPublicView(operationId);
    }
}
