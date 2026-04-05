package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record MonteCarloInferenceResponse(
        String mode,
        String phase,
        int sampleCount,
        List<McPlayerMarginal> playerMarginals,
        String note
) {
}
