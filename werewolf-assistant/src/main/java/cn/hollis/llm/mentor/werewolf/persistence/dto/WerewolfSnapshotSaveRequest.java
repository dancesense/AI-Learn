package cn.hollis.llm.mentor.werewolf.persistence.dto;

import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;

public record WerewolfSnapshotSaveRequest(
        Integer roundNumber,
        String phaseLabel,
        String snapshotType,
        WerewolfAnalysisRequest payload
) {
}
