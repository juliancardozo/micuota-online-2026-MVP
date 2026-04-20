package com.micuota.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false, length = 20)
    private String planCode;

    @Column(nullable = false)
    private Integer takeRateBps;

    @Column(nullable = false)
    private Integer advancedDunningFeeBps;

    @Column(nullable = false)
    private Boolean recoveryAutomationEnabled;

    @Column(nullable = false)
    private Boolean advancedAnalyticsEnabled;

    @Column(nullable = false)
    private Boolean integrationsEnabled;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public Integer getTakeRateBps() {
        return takeRateBps;
    }

    public void setTakeRateBps(Integer takeRateBps) {
        this.takeRateBps = takeRateBps;
    }

    public Integer getAdvancedDunningFeeBps() {
        return advancedDunningFeeBps;
    }

    public void setAdvancedDunningFeeBps(Integer advancedDunningFeeBps) {
        this.advancedDunningFeeBps = advancedDunningFeeBps;
    }

    public Boolean getRecoveryAutomationEnabled() {
        return recoveryAutomationEnabled;
    }

    public void setRecoveryAutomationEnabled(Boolean recoveryAutomationEnabled) {
        this.recoveryAutomationEnabled = recoveryAutomationEnabled;
    }

    public Boolean getAdvancedAnalyticsEnabled() {
        return advancedAnalyticsEnabled;
    }

    public void setAdvancedAnalyticsEnabled(Boolean advancedAnalyticsEnabled) {
        this.advancedAnalyticsEnabled = advancedAnalyticsEnabled;
    }

    public Boolean getIntegrationsEnabled() {
        return integrationsEnabled;
    }

    public void setIntegrationsEnabled(Boolean integrationsEnabled) {
        this.integrationsEnabled = integrationsEnabled;
    }
}
