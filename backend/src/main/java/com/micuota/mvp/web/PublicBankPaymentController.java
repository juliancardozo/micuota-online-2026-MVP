package com.micuota.mvp.web;

import com.micuota.mvp.service.BankTransferPaymentService;
import com.micuota.mvp.service.BankTransferPaymentService.BankAuthField;
import com.micuota.mvp.service.BankTransferPaymentService.BankOptions;
import com.micuota.mvp.service.BankTransferPaymentService.BankStep;
import com.micuota.mvp.service.BankTransferPaymentService.BankStepRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pago desde el banco del cliente (Prometeo Banking API). Publico como la pagina de pago: cada paso exige la
 * sesion opaca que devuelve el login, atada al cobro y con vencimiento de 10 minutos.
 */
@RestController
@RequestMapping("/api/public/payments/{operationId}/bank")
@Tag(name = "Pago desde el banco", description = "El cliente entra a su banco via Prometeo y transfiere a la cuenta del profesional")
public class PublicBankPaymentController {

    private final BankTransferPaymentService bankTransferPaymentService;

    public PublicBankPaymentController(BankTransferPaymentService bankTransferPaymentService) {
        this.bankTransferPaymentService = bankTransferPaymentService;
    }

    @GetMapping
    @Operation(summary = "Disponibilidad y bancos", description = "Indica si este cobro se puede pagar desde el banco y lista los bancos.")
    public BankOptions options(@PathVariable Long operationId) {
        return bankTransferPaymentService.options(operationId);
    }

    @GetMapping("/providers/{providerCode}/fields")
    @Operation(summary = "Datos para entrar al banco", description = "Campos que pide el banco elegido (usuario, contraseña, documento...).")
    public List<BankAuthField> fields(@PathVariable Long operationId, @PathVariable String providerCode) {
        return bankTransferPaymentService.providerFields(providerCode);
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar al banco", description = "Reenvia las credenciales a Prometeo sin guardarlas. Limitado a 5 intentos por cobro cada 15 minutos.")
    public BankStep login(@PathVariable Long operationId, @RequestBody BankStepRequest request) {
        return bankTransferPaymentService.login(operationId, request);
    }

    @PostMapping("/challenge")
    @Operation(summary = "Responder verificacion del banco", description = "OTP o pregunta de seguridad que pide el banco al entrar.")
    public BankStep challenge(@PathVariable Long operationId, @RequestBody BankStepRequest request) {
        return bankTransferPaymentService.answerChallenge(operationId, request);
    }

    @PostMapping("/client")
    @Operation(summary = "Elegir cliente o empresa", description = "Para bancos que agrupan cuentas por cliente.")
    public BankStep client(@PathVariable Long operationId, @RequestBody BankStepRequest request) {
        return bankTransferPaymentService.selectClient(operationId, request);
    }

    @PostMapping("/transfer")
    @Operation(summary = "Preparar la transferencia", description = "Desde la cuenta elegida hacia la cuenta del profesional, por el monto del cobro.")
    public BankStep transfer(@PathVariable Long operationId, @RequestBody BankStepRequest request) {
        return bankTransferPaymentService.transfer(operationId, request);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirmar la transferencia", description = "Con la autorizacion que pide el banco; al confirmarse el cobro queda pagado.")
    public BankStep confirm(@PathVariable Long operationId, @RequestBody BankStepRequest request) {
        return bankTransferPaymentService.confirm(operationId, request);
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancelar", description = "Cierra la sesion con el banco.")
    public Map<String, Boolean> cancel(@PathVariable Long operationId, @RequestBody BankStepRequest request) {
        bankTransferPaymentService.cancel(operationId, request.session());
        return Map.of("cancelled", true);
    }
}
