package cn.hollis.llm.mentor.werewolf.persistence.dto;

import java.time.LocalDateTime;

public record WerewolfGameResponse(
        Long id,
        String sessionUuid,
        Integer totalPlayers,
        String gameMode,
        String boardTemplateId,
        Integer myPlayerId,
        String myRoleHint,
        String winningObjective,
        String roleCompositionJson,
        String status,
        String outcomeNarrative,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long snapshotCount
) {
}
