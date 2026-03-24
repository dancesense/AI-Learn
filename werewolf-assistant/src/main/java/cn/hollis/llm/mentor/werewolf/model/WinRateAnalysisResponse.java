package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record WinRateAnalysisResponse(
        String mode,
        String phase,
        List<RoleWinRate> roleWinRates,
        String reasoningSummary
) {
}
