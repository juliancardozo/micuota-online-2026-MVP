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
@Table(name = "payment_operations")
public class PaymentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teacherId;

    @Column
    private Long studentUserId;

    @Column
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentFlowType flowType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal processingFeeAmount;

    @Column(nullable = false)
    private BigDecimal advancedFeatureFeeAmount;

    @Column(nullable = false)
    private BigDecimal netAmountForTeacher;

    @Column(nullable = false, length = 12)
    private String currency;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String checkoutUrl;

    @Column(nullable = false)
    private String providerReference;

    @Column
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawResponse;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime updatedAt;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(length = 120)
    private String failureReason;

    @Column
    private OffsetDateTime nextRetryAt;

    @Column
    private OffsetDateTime lastReminderAt;

    @Column
    private OffsetDateTime dueAt;

    @Column(nullable = false, length = 40)
    private String reconciliationStatus;

    @Column
    private OffsetDateTime lastReconciledAt;

    public Long getId() {
        return id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public PaymentProviderType getProvider() {
        return provider;
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

    public void setProvider(PaymentProviderType provider) {
        this.provider = provider;
    }

    public PaymentFlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(PaymentFlowType flowType) {
        this.flowType = flowType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getProcessingFeeAmount() {
        return processingFeeAmount;
    }

    public void setProcessingFeeAmount(BigDecimal processingFeeAmount) {
        this.processingFeeAmount = processingFeeAmount;
    }

    public BigDecimal getAdvancedFeatureFeeAmount() {
        return advancedFeatureFeeAmount;
    }

    public void setAdvancedFeatureFeeAmount(BigDecimal advancedFeatureFeeAmount) {
        this.advancedFeatureFeeAmount = advancedFeatureFeeAmount;
    }

    public BigDecimal getNetAmountForTeacher() {
        return netAmountForTeacher;
    }

    public void setNetAmountForTeacher(BigDecimal netAmountForTeacher) {
        this.netAmountForTeacher = netAmountForTeacher;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public OffsetDateTime getLastReminderAt() {
        return lastReminderAt;
    }

    public void setLastReminderAt(OffsetDateTime lastReminderAt) {
        this.lastReminderAt = lastReminderAt;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(OffsetDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public String getReconciliationStatus() {
        return reconciliationStatus;
    }

    public void setReconciliationStatus(String reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public OffsetDateTime getLastReconciledAt() {
        return lastReconciledAt;
    }

    public void setLastReconciledAt(OffsetDateTime lastReconciledAt) {
        this.lastReconciledAt = lastReconciledAt;
    }
}
