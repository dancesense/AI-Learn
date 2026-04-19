package cn.hollis.llm.mentor.werewolf.live.dto;

public record LiveChunkRequest(
        String transcript,
        String phase,
        Integer silenceSeconds,
        Integer speakerPlayerId,
        String myRoleHint
) {
}
