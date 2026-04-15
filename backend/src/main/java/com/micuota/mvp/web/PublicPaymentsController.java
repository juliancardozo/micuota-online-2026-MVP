package com.micuota.mvp.web;

import com.micuota.mvp.service.PaymentPublicView;
import com.micuota.mvp.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/payments")
public class PublicPaymentsController {

    private final PaymentService paymentService;

    public PublicPaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{operationId}")
    public PaymentPublicView getPaymentForClient(@PathVariable Long operationId) {
        return paymentService.getPublicView(operationId);
    }
}
