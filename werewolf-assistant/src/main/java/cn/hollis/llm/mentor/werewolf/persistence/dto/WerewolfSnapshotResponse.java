package cn.hollis.llm.mentor.werewolf.persistence.dto;

import java.time.LocalDateTime;

public record WerewolfSnapshotResponse(
        Long id,
        Long gameId,
        Integer roundNumber,
        String phaseLabel,
        String snapshotType,
        String requestPayload,
        LocalDateTime createdAt
) {
}
