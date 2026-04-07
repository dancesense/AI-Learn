package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record AdvancedTermsResponse(
        String rankTier,
        int unlockLevel,
        int totalUnlocked,
        List<AdvancedTermCard> cards
) {
}
