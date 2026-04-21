package com.micuota.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long operationId;

    @Column(nullable = false)
    private Long teacherId;

    @Column
    private Long studentUserId;

    @Column
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentFlowType flowType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private PaymentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private OperationStatus statusFrom;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private OperationStatus statusTo;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 12)
    private String currency;

    @Column(nullable = false)
    private String providerReference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getOperationId() {
        return operationId;
    }

    public void setOperationId(Long operationId) {
        this.operationId = operationId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public PaymentProviderType getProvider() {
        return provider;
    }

    public void setProvider(PaymentProviderType provider) {
        this.provider = provider;
    }

    public PaymentFlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(PaymentFlowType flowType) {
        this.flowType = flowType;
    }

    public PaymentEventType getEventType() {
        return eventType;
    }

    public void setEventType(PaymentEventType eventType) {
        this.eventType = eventType;
    }

    public OperationStatus getStatusFrom() {
        return statusFrom;
    }

    public void setStatusFrom(OperationStatus statusFrom) {
        this.statusFrom = statusFrom;
    }

    public OperationStatus getStatusTo() {
        return statusTo;
    }

    public void setStatusTo(OperationStatus statusTo) {
        this.statusTo = statusTo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
