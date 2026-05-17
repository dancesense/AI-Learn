package cn.hollis.llm.mentor.werewolf.live;

import cn.hollis.llm.mentor.werewolf.live.dto.CreateLiveSessionRequest;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveAnalyzeResponse;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveChunkRequest;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveSessionResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;

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

    /**
     * 仅保存发言文本，不触发AI分析（轻量快速）
     */
    @PostMapping("/sessions/{sessionId}/speech")
    public ResponseEntity<?> saveSpeech(@PathVariable Long sessionId,
                                        @RequestBody LiveChunkRequest request) {
        LiveChunkRequest safeReq = request == null
                ? new LiveChunkRequest("", "白天发言", null, null, "未知", "roundSummary", 1,
                        java.util.List.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 12)
                : request;
        String transcript = safeReq.transcript() == null ? "" : safeReq.transcript();
        log.info("[save-speech] sessionId={}, day={}, speaker={}, textLen={}, preview={}",
                sessionId, safeReq.day(), safeReq.speakerPlayerId(), transcript.length(), preview(transcript));
        try {
            liveAnalysisService.saveSpeech(sessionId, safeReq);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/chunks")
    public LiveAnalyzeResponse consumeChunk(@PathVariable Long sessionId,
                                            @RequestBody LiveChunkRequest request) {
        LiveChunkRequest safeReq = request == null
                ? new LiveChunkRequest("", "白天发言", null, null, "未知", "roundSummary", 1,
                        java.util.List.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 12)
                : request;
        String transcript = safeReq.transcript() == null ? "" : safeReq.transcript();
        log.info("[live-chunk] sessionId={}, day={}, phase={}, speaker={}, silenceSeconds={}, textLen={}, preview={}",
                sessionId, safeReq.day(), safeReq.phase(),
                safeReq.speakerPlayerId(), safeReq.silenceSeconds(),
                transcript.length(), preview(transcript));
        return liveAnalysisService.consumeChunk(sessionId, safeReq);
    }

    /**
     * 流式分析端点：先快速流式输出AI分析文字，再返回概率等结构化数据
     * 直接使用 HttpServletResponse 写 SSE，保证 UTF-8 编码
     */
    @PostMapping(value = "/sessions/{sessionId}/stream-chunks")
    public void streamChunk(@PathVariable Long sessionId,
                            @RequestBody LiveChunkRequest request,
                            HttpServletResponse response) throws IOException {
        LiveChunkRequest safeReq = request == null
                ? new LiveChunkRequest("", "白天发言", null, null, "未知", "roundSummary", 1,
                        java.util.List.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of(), "", 12)
                : request;
        String transcript = safeReq.transcript() == null ? "" : safeReq.transcript();
        log.info("[stream-chunk] sessionId={}, day={}, phase={}, speaker={}, analysisType={}, textLen={}, preview={}",
                sessionId, safeReq.day(), safeReq.phase(), safeReq.speakerPlayerId(), safeReq.analysisType(),
                transcript.length(), preview(transcript));

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = response.getWriter();
        try {
            liveAnalysisService.consumeChunkStreaming(sessionId, safeReq, (eventName, jsonData) -> {
                writer.write("event:" + eventName + "\ndata:" + jsonData + "\n\n");
                writer.flush();
            });
        } catch (Exception ex) {
            log.error("[stream-chunk] error sessionId={}", sessionId, ex);
            String errorJson = "{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
            writer.write("event:error\ndata:" + errorJson + "\n\n");
            writer.flush();
        }
    }

    /**
     * 获取指定天的发言记录
     */
    @GetMapping("/sessions/{sessionId}/speeches")
    public ResponseEntity<?> getSpeechesByDay(@PathVariable Long sessionId,
                                              @RequestParam(defaultValue = "1") Integer day) {
        try {
            return ResponseEntity.ok(liveAnalysisService.getSpeechesByDay(sessionId, day));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/sessions/{sessionId}/review")
    public ResponseEntity<?> getSessionReview(@PathVariable Long sessionId) {
        try {
            return ResponseEntity.ok(liveAnalysisService.getSessionReview(sessionId));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        String normalized = text.replace('\n', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }
}
