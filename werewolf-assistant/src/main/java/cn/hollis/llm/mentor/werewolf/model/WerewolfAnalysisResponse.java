package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record WerewolfAnalysisResponse(
        String mode,
        String phase,
        List<PlayerRoleAssessment> playerAssessments,
        List<RoleWinRate> roleWinRates,
        SpeechStrategy speechStrategy,
        String reasoningSummary
) {
}
