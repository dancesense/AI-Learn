package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;
import java.util.Map;

public record WerewolfAnalysisRequest(
        Integer totalPlayers,
        String gameMode,
        String phase,
        Integer myPlayerId,
        String myRoleHint,
        String winningObjective,
        Map<String, Integer> roleComposition,
        List<PlayerSpeech> speeches,
        String extraContext,
        List<Integer> deadPlayers,
        Map<Integer, String> revealedIdentities,
        List<Integer> knownWerewolfPlayers
) {
}
