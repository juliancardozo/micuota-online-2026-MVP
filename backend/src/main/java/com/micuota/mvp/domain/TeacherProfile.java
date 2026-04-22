package com.micuota.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "teacher_profiles")
public class TeacherProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String displayName;

    @Column(name = "mp_access_token")
    private String mpAccessToken;

    @Column(name = "mp_public_key")
    private String mpPublicKey;

    @Column(name = "wc_api_key")
    private String wooCommerceApiKey;

    @Column(name = "prometeo_api_key")
    private String prometeoApiKey;

    @Column(name = "transfer_alias")
    private String transferAlias;

    @Column(name = "transfer_bank_name")
    private String transferBankName;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMpAccessToken() {
        return mpAccessToken;
    }

    public void setMpAccessToken(String mpAccessToken) {
        this.mpAccessToken = mpAccessToken;
    }

    public String getMpPublicKey() {
        return mpPublicKey;
    }

    public void setMpPublicKey(String mpPublicKey) {
        this.mpPublicKey = mpPublicKey;
    }

    public String getWooCommerceApiKey() {
        return wooCommerceApiKey;
    }

    public void setWooCommerceApiKey(String wooCommerceApiKey) {
        this.wooCommerceApiKey = wooCommerceApiKey;
    }

    public String getPrometeoApiKey() {
        return prometeoApiKey;
    }

    public void setPrometeoApiKey(String prometeoApiKey) {
        this.prometeoApiKey = prometeoApiKey;
    }

    public String getTransferAlias() {
        return transferAlias;
    }

    public void setTransferAlias(String transferAlias) {
        this.transferAlias = transferAlias;
    }

    public String getTransferBankName() {
        return transferBankName;
    }

    public void setTransferBankName(String transferBankName) {
        this.transferBankName = transferBankName;
    }
}
