package cn.hollis.llm.mentor.werewolf.live.dto;

import java.util.List;
import java.util.Map;

public record LiveChunkRequest(
        String transcript,
        String phase,
        Integer silenceSeconds,
        Integer speakerPlayerId,
        String myRoleHint,
        String analysisType,
        Integer day,
        List<String> skillLogs,
        List<Map<String, Object>> voteRecords,
        Map<Integer, Boolean> playerAlive,
        Map<Integer, String> playerRoles,
        String previousDaysSummary,
        Integer totalPlayers
) {
    public LiveChunkRequest {
        if (analysisType == null || analysisType.isBlank()) {
            analysisType = "roundSummary";
        }
        if (day == null || day < 1) {
            day = 1;
        }
        if (skillLogs == null) {
            skillLogs = List.of();
        }
        if (voteRecords == null) {
            voteRecords = List.of();
        }
        if (playerAlive == null) {
            playerAlive = Map.of();
        }
        if (playerRoles == null) {
            playerRoles = Map.of();
        }
        if (previousDaysSummary == null) {
            previousDaysSummary = "";
        }
        if (totalPlayers == null || totalPlayers < 1) {
            totalPlayers = 12;
        }
    }
}
