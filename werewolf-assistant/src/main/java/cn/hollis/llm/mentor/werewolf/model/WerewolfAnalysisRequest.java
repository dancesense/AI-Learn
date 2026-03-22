package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record WerewolfAnalysisRequest(
        Integer totalPlayers,
        String gameMode,
        String phase,
        Integer myPlayerId,
        String myRoleHint,
        String winningObjective,
        List<PlayerSpeech> speeches,
        String extraContext
) {
}
