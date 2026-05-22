package cn.hollis.llm.mentor.werewolf.live.dto;

import java.util.Map;

public record CreateLiveSessionRequest(
        String sessionUuid,
        Integer totalPlayers,
        String gameMode,
        Integer myPlayerId,
        String myRoleHint,
        Map<String, Integer> roleComposition
) {
}
