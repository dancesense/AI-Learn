package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record PsychologyCoachResponse(
        String mode,
        String phase,
        List<String> observationChecklist,
        List<String> pressureQuestions,
        List<String> predictedReactions,
        List<String> counterStrategies,
        String reasoningSummary
) {
}
