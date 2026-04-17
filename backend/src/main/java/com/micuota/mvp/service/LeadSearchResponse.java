package com.micuota.mvp.service;

import java.util.List;

public record LeadSearchResponse(List<LeadView> data, int total) {
}
