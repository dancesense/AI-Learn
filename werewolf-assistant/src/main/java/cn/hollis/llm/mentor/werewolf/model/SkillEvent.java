package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

/**
 * @param nightOrDay 第几夜或第几天，可为 null
 * @param phaseTag   如 狼人刀人、女巫救、预言家验
 */
public record SkillEvent(
        Integer nightOrDay,
        String phaseTag,
        String actionType,
        Integer actorPlayerId,
        List<Integer> targetPlayerIds,
        String details
) {
}
