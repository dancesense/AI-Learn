package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

/**
 * 蒙特卡洛边缘分布：某号位各角色出现频率。
 */
public record McPlayerMarginal(
        Integer playerId,
        List<RoleProbability> roleProbabilities,
        Double wolfProbability
) {
}
