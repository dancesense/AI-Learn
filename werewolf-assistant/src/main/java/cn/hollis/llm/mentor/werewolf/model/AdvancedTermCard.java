package cn.hollis.llm.mentor.werewolf.model;

public record AdvancedTermCard(
        String term,
        String category,
        String level,
        String definition,
        String usageScenario,
        String example
) {
}
