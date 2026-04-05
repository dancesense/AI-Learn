package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

/**
 * @param intensity0to100 建议情绪强度 0-100
 */
public record EmotionGuide(
        Integer intensity0to100,
        String postureSummary,
        List<String> actingTips
) {
}
