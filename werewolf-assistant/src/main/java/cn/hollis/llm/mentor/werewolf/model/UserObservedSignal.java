package cn.hollis.llm.mentor.werewolf.model;

/**
 * 用户自行勾选或填写的现场观察（微表情/语态等），供 AI 与话术模块参考。
 */
public record UserObservedSignal(
        Integer playerId,
        String category,
        String description
) {
}
