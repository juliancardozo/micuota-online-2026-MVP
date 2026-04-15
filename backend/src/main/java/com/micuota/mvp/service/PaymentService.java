package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.PaymentFlowType;
import com.micuota.mvp.domain.PaymentOperation;
import com.micuota.mvp.domain.PaymentProviderType;
import com.micuota.mvp.domain.TeacherProfile;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final Map<PaymentProviderType, PaymentProviderGateway> gateways;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public PaymentService(
        TeacherProfileRepository teacherProfileRepository,
        PaymentOperationRepository paymentOperationRepository,
        List<PaymentProviderGateway> providers
    ) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentOperationRepository = paymentOperationRepository;
        this.gateways = new EnumMap<>(PaymentProviderType.class);
        providers.forEach(p -> this.gateways.put(p.provider(), p));
    }

    @Transactional
    public PaymentOperation createOneTime(CreatePaymentRequest request) {
        return createOperation(PaymentFlowType.ONE_TIME, request);
    }

    @Transactional
    public PaymentOperation createSubscription(CreatePaymentRequest request) {
        return createOperation(PaymentFlowType.SUBSCRIPTION, request);
    }

    @Transactional(readOnly = true)
    public List<PaymentOperation> lastOperationsByTeacher(Long teacherId) {
        return paymentOperationRepository.findTop20ByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Transactional
    public PaymentOperation updateStatus(Long operationId, OperationStatus status) {
        PaymentOperation operation = paymentOperationRepository.findById(operationId)
            .orElseThrow(() -> new IllegalArgumentException("Operacion no encontrada: " + operationId));
        operation.setStatus(status);
        operation.setUpdatedAt(OffsetDateTime.now());
        return paymentOperationRepository.save(operation);
    }

    private PaymentOperation createOperation(PaymentFlowType flowType, CreatePaymentRequest request) {
        TeacherProfile teacher = teacherProfileRepository.findById(request.teacherId())
            .orElseThrow(() -> new IllegalArgumentException("Teacher no encontrado: " + request.teacherId()));

        PaymentProviderGateway gateway = gateways.get(request.provider());
        if (gateway == null) {
            throw new IllegalArgumentException("Proveedor no soportado: " + request.provider());
        }

        TeacherProviderCredentials credentials = new TeacherProviderCredentials(
            teacher.getMpAccessToken(),
            teacher.getPrometeoApiKey(),
            teacher.getWooCommerceApiKey()
        );

        ProviderCheckoutResult result = gateway.createCheckout(flowType, request, credentials, appBaseUrl);

        PaymentOperation operation = new PaymentOperation();
        operation.setTeacherId(request.teacherId());
        operation.setProvider(request.provider());
        operation.setFlowType(flowType);
        operation.setAmount(request.amount());
        operation.setCurrency(request.currency());
        operation.setDescription(request.description());
        operation.setCheckoutUrl(result.checkoutUrl());
        operation.setProviderReference(result.providerReference());
        operation.setRawResponse(result.rawResponse());
        operation.setStatus(OperationStatus.CREATED);
        operation.setCreatedAt(OffsetDateTime.now());

        return paymentOperationRepository.save(operation);
    }
}
