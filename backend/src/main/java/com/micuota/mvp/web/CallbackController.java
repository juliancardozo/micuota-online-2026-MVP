package com.micuota.mvp.web;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/callbacks")
@Tag(name = "Callbacks", description = "Callbacks minimos para actualizar estado de operaciones de pago")
public class CallbackController {

    private final PaymentService paymentService;

    public CallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/success")
    @Operation(summary = "Callback success", description = "Actualiza la operacion como SUCCESS.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Operacion inexistente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PaymentOperation success(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.SUCCESS);
    }

    @GetMapping("/success/by-reference")
    @Operation(summary = "Callback success por providerReference", description = "Actualiza la operacion como SUCCESS segun referencia del proveedor.")
    public PaymentOperation successByReference(@RequestParam String providerReference) {
        return paymentService.updateStatusByProviderReference(providerReference, OperationStatus.SUCCESS);
    }

    @GetMapping("/pending")
    @Operation(summary = "Callback pending", description = "Actualiza la operacion como PENDING.")
    public PaymentOperation pending(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.PENDING);
    }

    @GetMapping("/pending/by-reference")
    @Operation(summary = "Callback pending por providerReference", description = "Actualiza la operacion como PENDING segun referencia del proveedor.")
    public PaymentOperation pendingByReference(@RequestParam String providerReference) {
        return paymentService.updateStatusByProviderReference(providerReference, OperationStatus.PENDING);
    }

    @GetMapping("/failure")
    @Operation(summary = "Callback failure", description = "Actualiza la operacion como FAILURE.")
    public PaymentOperation failure(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.FAILURE);
    }

    @GetMapping("/failed")
    @Operation(summary = "Callback failed", description = "Alias de failure para compatibilidad de pasarelas.")
    public PaymentOperation failed(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.FAILURE);
    }

    @GetMapping("/failure/by-reference")
    @Operation(summary = "Callback failure por providerReference", description = "Actualiza la operacion como FAILURE segun referencia del proveedor.")
    public PaymentOperation failureByReference(@RequestParam String providerReference) {
        return paymentService.updateStatusByProviderReference(providerReference, OperationStatus.FAILURE);
    }

    @GetMapping("/failed/by-reference")
    @Operation(summary = "Callback failed por providerReference", description = "Alias de failure por referencia para compatibilidad.")
    public PaymentOperation failedByReference(@RequestParam String providerReference) {
        return paymentService.updateStatusByProviderReference(providerReference, OperationStatus.FAILURE);
    }
}
