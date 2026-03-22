package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record PlayerRoleAssessment(
        Integer playerId,
        String likelyRole,
        Double confidence,
        List<RoleProbability> roleProbabilities,
        List<String> keyEvidence
) {
}
