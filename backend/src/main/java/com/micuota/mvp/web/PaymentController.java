package com.micuota.mvp.web;

import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.CreatePaymentRequest;
import com.micuota.mvp.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments Legacy", description = "Endpoints de pago directos sin sesion por token (compatibilidad MVP)")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/one-time")
    @Operation(summary = "Crear pago unico (legacy)", description = "Crea una operacion ONE_TIME usando teacherId en payload.")
    public PaymentOperation createOneTime(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createOneTime(request);
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Crear suscripcion (legacy)", description = "Crea una operacion SUBSCRIPTION usando teacherId en payload.")
    public PaymentOperation createSubscription(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createSubscription(request);
    }

    @GetMapping("/teacher/{teacherId}")
    @Operation(summary = "Listar operaciones por teacherId (legacy)")
    public List<PaymentOperation> listTeacherOperations(@PathVariable Long teacherId) {
        return paymentService.lastOperationsByTeacher(teacherId);
    }
}
