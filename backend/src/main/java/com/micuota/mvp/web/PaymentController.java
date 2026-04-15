package com.micuota.mvp.web;

import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.CreatePaymentRequest;
import com.micuota.mvp.service.PaymentService;
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
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/one-time")
    public PaymentOperation createOneTime(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createOneTime(request);
    }

    @PostMapping("/subscriptions")
    public PaymentOperation createSubscription(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createSubscription(request);
    }

    @GetMapping("/teacher/{teacherId}")
    public List<PaymentOperation> listTeacherOperations(@PathVariable Long teacherId) {
        return paymentService.lastOperationsByTeacher(teacherId);
    }
}
