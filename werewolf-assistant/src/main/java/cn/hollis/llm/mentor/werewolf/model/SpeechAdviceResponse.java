package cn.hollis.llm.mentor.werewolf.model;

public record SpeechAdviceResponse(
        String mode,
        String phase,
        SpeechStrategy speechStrategy,
        String reasoningSummary
) {
}
