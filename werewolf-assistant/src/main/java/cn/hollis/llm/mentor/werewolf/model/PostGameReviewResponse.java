package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record PostGameReviewResponse(
        String mode,
        String outcomeSummary,
        List<String> logicVulnerabilities,
        List<String> emotionVulnerabilities,
        List<String> decisionMistakes,
        List<String> improvementSuggestions,
        List<String> learningResources,
        String reasoningSummary
) {
}
