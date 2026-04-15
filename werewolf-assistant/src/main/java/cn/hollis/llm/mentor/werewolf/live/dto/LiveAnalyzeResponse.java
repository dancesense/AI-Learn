package cn.hollis.llm.mentor.werewolf.live.dto;

import java.util.List;

public record LiveAnalyzeResponse(
        Long sessionId,
        Long elapsedSeconds,
        Integer currentSpeakerId,
        String silenceAlert,
        List<LiveEventView> events,
        List<PlayerProbabilityBar> probabilities,
        String suggestedSpeech,
        String voteAdvice,
        List<String> votePoints,
        List<String> werewolfTalks
) {
}
