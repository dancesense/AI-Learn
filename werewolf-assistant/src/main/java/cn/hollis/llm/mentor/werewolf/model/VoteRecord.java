package cn.hollis.llm.mentor.werewolf.model;

/**
 * @param targetId 被投票玩家；null 表示弃票或未归票
 */
public record VoteRecord(
        Integer round,
        String voteType,
        Integer voterId,
        Integer targetId
) {
}
