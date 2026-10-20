package com.micuota.mvp.service;

import com.micuota.mvp.domain.OperationStatus;
import com.micuota.mvp.domain.Tenant;
import com.micuota.mvp.repository.PaymentOperationRepository;
import com.micuota.mvp.repository.TeacherProfileRepository;
import com.micuota.mvp.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comisiones de MiCuota por tenant: tasas configuradas y lo efectivamente cobrado via marketplace_fee. */
@Service
public class AdminBillingService {

    private final TenantRepository tenantRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PaymentOperationRepository paymentOperationRepository;

    public AdminBillingService(
        TenantRepository tenantRepository,
        TeacherProfileRepository teacherProfileRepository,
        PaymentOperationRepository paymentOperationRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.paymentOperationRepository = paymentOperationRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminTenantBillingView> listTenants() {
        Map<Long, Object[]> fees = new HashMap<>();
        for (Object[] row : paymentOperationRepository.sumMarketplaceFeesByTenant(OperationStatus.SUCCESS)) {
            fees.put((Long) row[0], row);
        }
        return tenantRepository.findAll().stream()
            .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
            .map(tenant -> toView(tenant, fees.get(tenant.getId())))
            .toList();
    }

    @Transactional
    public AdminTenantBillingView updateBilling(Long tenantId, UpdateTenantBillingSettingsRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
        tenant.setPlanCode(request.planCode().trim().toUpperCase());
        if (request.takeRateBps() != null) {
            tenant.setTakeRateBps(request.takeRateBps());
        }
        if (request.advancedDunningFeeBps() != null) {
            tenant.setAdvancedDunningFeeBps(request.advancedDunningFeeBps());
        }
        if (request.recoveryAutomationEnabled() != null) {
            tenant.setRecoveryAutomationEnabled(request.recoveryAutomationEnabled());
        }
        if (request.advancedAnalyticsEnabled() != null) {
            tenant.setAdvancedAnalyticsEnabled(request.advancedAnalyticsEnabled());
        }
        if (request.integrationsEnabled() != null) {
            tenant.setIntegrationsEnabled(request.integrationsEnabled());
        }
        Tenant saved = tenantRepository.save(tenant);
        Object[] fee = paymentOperationRepository.sumMarketplaceFeesByTenant(OperationStatus.SUCCESS).stream()
            .filter(row -> saved.getId().equals(row[0]))
            .findFirst()
            .orElse(null);
        return toView(saved, fee);
    }

    private AdminTenantBillingView toView(Tenant tenant, Object[] fee) {
        return new AdminTenantBillingView(
            tenant.getId(),
            tenant.getName(),
            tenant.getSlug(),
            tenant.getPlanCode() == null ? "BASE" : tenant.getPlanCode(),
            tenant.getTakeRateBps() == null ? 350 : tenant.getTakeRateBps(),
            tenant.getAdvancedDunningFeeBps() == null ? 120 : tenant.getAdvancedDunningFeeBps(),
            Boolean.TRUE.equals(tenant.getRecoveryAutomationEnabled()),
            teacherProfileRepository.countByUserTenantId(tenant.getId()),
            teacherProfileRepository.countByUserTenantIdAndMpOauthConnectedTrue(tenant.getId()),
            fee == null ? 0 : ((Number) fee[2]).longValue(),
            fee == null ? BigDecimal.ZERO : new BigDecimal(fee[1].toString())
        );
    }
}
