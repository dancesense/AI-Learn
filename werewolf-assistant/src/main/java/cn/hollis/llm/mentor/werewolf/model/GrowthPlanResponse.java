package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record GrowthPlanResponse(
        String playerLabel,
        String stageAssessment,
        List<String> weaknessTags,
        List<String> dailyMissions,
        List<String> emergencySpeechTemplates,
        List<String> transferableSkills
) {
}
