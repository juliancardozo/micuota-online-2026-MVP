package com.micuota.mvp.web;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/callbacks")
public class CallbackController {

    private final PaymentService paymentService;

    public CallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/success")
    public PaymentOperation success(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.SUCCESS);
    }

    @GetMapping("/pending")
    public PaymentOperation pending(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.PENDING);
    }

    @GetMapping("/failure")
    public PaymentOperation failure(@RequestParam Long operationId) {
        return paymentService.updateStatus(operationId, OperationStatus.FAILURE);
    }
}
