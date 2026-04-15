package cn.hollis.llm.mentor.werewolf.live.dto;

import java.time.LocalDateTime;

public record LiveEventView(
        String eventType,
        Integer speakerPlayerId,
        String speakerLabel,
        String content,
        Boolean highlight,
        LocalDateTime createdAt
) {
}

