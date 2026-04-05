package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record SpeechAdviceResponse(
        String mode,
        String phase,
        SpeechStrategy speechStrategy,
        String reasoningSummary,
        EmotionGuide emotionGuide,
        List<String> defenseSpeechTemplates,
        List<String> attackSpeechTemplates,
        List<String> tableWaterTemplates
) {
}
