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

    @GetMapping("/pending")
    @Operation(summary = "Callback pending", description = "Actualiza la operacion como PENDING.")
    public PaymentOperation pending(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.PENDING);
    }

    @GetMapping("/failure")
    @Operation(summary = "Callback failure", description = "Actualiza la operacion como FAILURE.")
    public PaymentOperation failure(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.FAILURE);
    }
}
