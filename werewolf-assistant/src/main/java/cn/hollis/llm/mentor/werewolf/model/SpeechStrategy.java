package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record SpeechStrategy(
        String objective,
        String suggestedSpeech,
        List<String> tacticalPoints,
        List<String> forbiddenPoints
) {
}
