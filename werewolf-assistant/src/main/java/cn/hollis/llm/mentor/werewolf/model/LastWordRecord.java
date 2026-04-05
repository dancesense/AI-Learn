package cn.hollis.llm.mentor.werewolf.model;

public record LastWordRecord(
        Integer playerId,
        Integer roundOrDay,
        String content
) {
}
