package cn.hollis.llm.mentor.werewolf.persistence.dto;

import java.util.Map;

public record CreateGameRequest(
        String sessionUuid,
        Integer totalPlayers,
        String gameMode,
        String boardTemplateId,
        Integer myPlayerId,
        String myRoleHint,
        String winningObjective,
        Map<String, Integer> roleComposition
) {
}
