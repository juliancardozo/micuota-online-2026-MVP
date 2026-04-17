package com.micuota.mvp.service;

import com.micuota.mvp.domain.Lead;
import com.micuota.mvp.repository.LeadRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrmLeadService {

    private final LeadRepository leadRepository;
    private final String defaultOwner;

    public CrmLeadService(
        LeadRepository leadRepository,
        @Value("${app.crm.default-owner:ventas@micuota.online}") String defaultOwner
    ) {
        this.leadRepository = leadRepository;
        this.defaultOwner = defaultOwner;
    }

    public LeadView saveLead(CreateLeadRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        Lead lead = leadRepository.findByEmailIgnoreCase(request.email())
            .orElseGet(Lead::new);

        boolean isNewLead = lead.getId() == null;
        lead.setEmail(normalizeEmail(request.email()));
        lead.setPhone(blankToNull(request.phone()));
        lead.setFullName(request.fullName().trim());
        lead.setSource(request.source().trim());
        lead.setUpdatedAt(now);

        if (isNewLead) {
            lead.setCreatedAt(now);
            lead.setStatus("NEW");
        }

        return LeadView.from(leadRepository.save(lead));
    }

    public LeadSearchResponse searchByEmail(String email) {
        if (email == null || email.isBlank()) {
            List<LeadView> leads = leadRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(LeadView::from)
                .toList();
            return new LeadSearchResponse(leads, leads.size());
        }

        List<LeadView> leads = leadRepository.findByEmailIgnoreCase(email.trim())
            .stream()
            .map(LeadView::from)
            .toList();
        return new LeadSearchResponse(leads, leads.size());
    }

    public LeadView assignLead(AssignLeadRequest request) {
        Lead lead = leadRepository.findByEmailIgnoreCase(request.email().trim())
            .orElseThrow(() -> new IllegalArgumentException("Lead no encontrado para email: " + request.email().trim()));

        OffsetDateTime now = OffsetDateTime.now();
        lead.setSegment(request.segment().trim().toUpperCase());
        lead.setScore(request.score());
        lead.setOwner(resolveOwner(request.segment()));
        lead.setStatus("ASSIGNED");
        lead.setAssignedAt(now);
        lead.setUpdatedAt(now);

        return LeadView.from(leadRepository.save(lead));
    }

    private String resolveOwner(String segment) {
        String normalizedSegment = segment == null ? "" : segment.trim().toUpperCase();
        return switch (normalizedSegment) {
            case "SQL" -> "closer@micuota.online";
            case "MQL" -> "advisor@micuota.online";
            default -> defaultOwner;
        };
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
