package cn.hollis.llm.mentor.werewolf.live.dto;

public record LiveSessionResponse(
        Long sessionId,
        String sessionUuid,
        String status
) {
}

