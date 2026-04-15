package cn.hollis.llm.mentor.werewolf.live;

import cn.hollis.llm.mentor.werewolf.live.dto.CreateLiveSessionRequest;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveAnalyzeResponse;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveChunkRequest;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/werewolf/live")
public class WerewolfLiveController {

    private static final Logger log = LoggerFactory.getLogger(WerewolfLiveController.class);

    private final WerewolfLiveAnalysisService liveAnalysisService;

    public WerewolfLiveController(WerewolfLiveAnalysisService liveAnalysisService) {
        this.liveAnalysisService = liveAnalysisService;
    }

    @PostMapping("/sessions")
    public LiveSessionResponse createSession(@RequestBody(required = false) CreateLiveSessionRequest request) {
        CreateLiveSessionRequest safeReq = request == null
                ? new CreateLiveSessionRequest(null, 12, "12人标准局", 1, "未知")
                : request;
        return liveAnalysisService.createSession(safeReq);
    }

    @PostMapping("/sessions/{sessionId}/chunks")
    public LiveAnalyzeResponse consumeChunk(@PathVariable Long sessionId,
                                            @RequestBody LiveChunkRequest request) {
        LiveChunkRequest safeReq = request == null
                ? new LiveChunkRequest("", "白天发言", null, null)
                : request;
        String transcript = safeReq.transcript() == null ? "" : safeReq.transcript();
        log.info("[live-chunk] sessionId={}, phase={}, speaker={}, silenceSeconds={}, textLen={}, preview={}",
                sessionId,
                safeReq.phase(),
                safeReq.speakerPlayerId(),
                safeReq.silenceSeconds(),
                transcript.length(),
                preview(transcript));
        return liveAnalysisService.consumeChunk(sessionId, safeReq);
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        String normalized = text.replace('\n', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }
}
