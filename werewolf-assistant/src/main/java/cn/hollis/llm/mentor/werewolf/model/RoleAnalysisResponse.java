package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record RoleAnalysisResponse(
        String mode,
        String phase,
        List<PlayerRoleAssessment> playerAssessments,
        String reasoningSummary
) {
}
