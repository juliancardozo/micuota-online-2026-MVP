package com.micuota.mvp.service;

import java.util.List;

public record LaunchpadView(
    String planName,
    String stage,
    int activationScore,
    int completedSteps,
    int totalSteps,
    int experiencePoints,
    String headline,
    String nextBestAction,
    String nextReward,
    String ahaMomentDefinition,
    String freemiumNote,
    LaunchpadUsageView usage,
    List<LaunchpadChecklistItemView> checklist,
    List<LaunchpadExperimentView> experiments
) {
}
