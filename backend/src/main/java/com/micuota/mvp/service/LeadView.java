package com.micuota.mvp.service;

import com.micuota.mvp.domain.Lead;
import java.time.OffsetDateTime;

public record LeadView(
    Long id,
    String email,
    String phone,
    String fullName,
    String source,
    String status,
    String segment,
    Integer score,
    String owner,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime assignedAt
) {

    public static LeadView from(Lead lead) {
        return new LeadView(
            lead.getId(),
            lead.getEmail(),
            lead.getPhone(),
            lead.getFullName(),
            lead.getSource(),
            lead.getStatus(),
            lead.getSegment(),
            lead.getScore(),
            lead.getOwner(),
            lead.getCreatedAt(),
            lead.getUpdatedAt(),
            lead.getAssignedAt()
        );
    }
}
