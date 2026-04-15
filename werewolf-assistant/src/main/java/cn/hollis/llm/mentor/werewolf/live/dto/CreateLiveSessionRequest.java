package cn.hollis.llm.mentor.werewolf.live.dto;

public record CreateLiveSessionRequest(
        String sessionUuid,
        Integer totalPlayers,
        String gameMode,
        Integer myPlayerId,
        String myRoleHint
) {
}

