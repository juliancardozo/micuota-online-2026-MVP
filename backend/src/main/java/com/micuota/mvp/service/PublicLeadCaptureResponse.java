package com.micuota.mvp.service;

public record PublicLeadCaptureResponse(
    Long leadId,
    String email,
    String status,
    boolean isNewLead,
    String message
) {
}
