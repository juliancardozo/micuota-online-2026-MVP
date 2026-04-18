package com.micuota.mvp.service;

public record LaunchpadChecklistItemView(
    String key,
    String title,
    boolean done,
    String detail,
    int points,
    String actionLabel,
    String actionTarget
) {
}
