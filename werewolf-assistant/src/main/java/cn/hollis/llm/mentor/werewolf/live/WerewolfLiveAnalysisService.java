package cn.hollis.llm.mentor.werewolf.live;

import cn.hollis.llm.mentor.werewolf.live.dto.CreateLiveSessionRequest;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveAnalyzeResponse;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveChunkRequest;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveEventView;
import cn.hollis.llm.mentor.werewolf.live.dto.LiveSessionResponse;
import cn.hollis.llm.mentor.werewolf.live.dto.PlayerProbabilityBar;
import cn.hollis.llm.mentor.werewolf.live.entity.WerewolfLiveEventEntity;
import cn.hollis.llm.mentor.werewolf.live.entity.WerewolfLiveSessionEntity;
import cn.hollis.llm.mentor.werewolf.live.repo.WerewolfLiveEventRepository;
import cn.hollis.llm.mentor.werewolf.live.repo.WerewolfLiveSessionRepository;
import cn.hollis.llm.mentor.werewolf.model.PlayerRoleAssessment;
import cn.hollis.llm.mentor.werewolf.model.PlayerSpeech;
import cn.hollis.llm.mentor.werewolf.model.RoleAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.RoleProbability;
import cn.hollis.llm.mentor.werewolf.model.SpeechAdviceResponse;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WinRateAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.service.WerewolfAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WerewolfLiveAnalysisService {

    private static final Pattern TURN_SPEAKER_PATTERN = Pattern.compile("(请|轮到)?\\s*(\\d+)\\s*号玩家?发言");

    private final WerewolfLiveSessionRepository sessionRepository;
    private final WerewolfLiveEventRepository eventRepository;
    private final WerewolfAnalysisService analysisService;

    public WerewolfLiveAnalysisService(WerewolfLiveSessionRepository sessionRepository,
                                       WerewolfLiveEventRepository eventRepository,
                                       WerewolfAnalysisService analysisService) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.analysisService = analysisService;
    }

    @Transactional
    public LiveSessionResponse createSession(CreateLiveSessionRequest request) {
        String sessionUuid = StringUtils.hasText(request.sessionUuid())
                ? request.sessionUuid().trim()
                : UUID.randomUUID().toString();

        WerewolfLiveSessionEntity session = sessionRepository.findBySessionUuid(sessionUuid)
                .orElseGet(WerewolfLiveSessionEntity::new);

        session.setSessionUuid(sessionUuid);
        session.setStatus("ACTIVE");
        session.setTotalPlayers(request.totalPlayers() == null ? 12 : request.totalPlayers());
        session.setGameMode(StringUtils.hasText(request.gameMode()) ? request.gameMode().trim() : "12人标准局");
        session.setMyPlayerId(request.myPlayerId() == null ? 1 : request.myPlayerId());
        session.setMyRoleHint(StringUtils.hasText(request.myRoleHint()) ? request.myRoleHint().trim() : "未知");

        sessionRepository.save(session);
        return new LiveSessionResponse(session.getId(), session.getSessionUuid(), session.getStatus());
    }

    @Transactional
    public LiveAnalyzeResponse consumeChunk(Long sessionId, LiveChunkRequest request) {
        WerewolfLiveSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("实时会话不存在: " + sessionId));

        String transcript = request.transcript() == null ? "" : request.transcript().trim();
        Integer detectedSpeakerId = detectSpeakerIdFromPrompt(transcript);
        if (detectedSpeakerId != null) {
            session.setCurrentSpeakerId(detectedSpeakerId);
            saveEvent(session, "SYSTEM_PROMPT", detectedSpeakerId, detectedSpeakerId + "号", transcript, null, false);
        }

        boolean purePrompt = isPurePrompt(transcript);
        Integer speakerId = request.speakerPlayerId() != null
                ? request.speakerPlayerId()
                : (detectedSpeakerId != null ? detectedSpeakerId : session.getCurrentSpeakerId());

        if (StringUtils.hasText(transcript) && !purePrompt) {
            String speakerLabel = speakerId == null ? "未知玩家" : speakerId + "号";
            saveEvent(session, "PLAYER_SPEECH", speakerId, speakerLabel, transcript, null, false);
        }

        List<WerewolfLiveEventEntity> playerEvents = eventRepository
                .findTop120BySession_IdAndEventTypeOrderByCreatedAtAsc(sessionId, "PLAYER_SPEECH");

        RoleAnalysisResponse roleAnalysisResponse = null;
        SpeechAdviceResponse speechAdviceResponse = null;
        WinRateAnalysisResponse winRateAnalysisResponse = null;

        if (!playerEvents.isEmpty()) {
            WerewolfAnalysisRequest aiRequest = buildAnalysisRequest(session, playerEvents, request.phase());
            roleAnalysisResponse = analysisService.analyzePlayerRoles(aiRequest);
            speechAdviceResponse = analysisService.analyzeSpeechAdvice(aiRequest);
            winRateAnalysisResponse = analysisService.analyzeWinRates(aiRequest);
            String aiMessage = buildAiMessage(roleAnalysisResponse, speechAdviceResponse, winRateAnalysisResponse);
            saveEvent(session, "AI_INSIGHT", null, "AI", aiMessage, null, true);
        }

        sessionRepository.save(session);

        List<LiveEventView> events = eventRepository.findTop80BySession_IdOrderByCreatedAtDesc(sessionId).stream()
                .sorted(Comparator.comparing(WerewolfLiveEventEntity::getCreatedAt))
                .map(this::toEventView)
                .toList();

        List<PlayerProbabilityBar> bars = buildProbabilityBars(roleAnalysisResponse);
        String suggestedSpeech = extractSuggestedSpeech(speechAdviceResponse);
        String voteAdvice = buildVoteAdvice(roleAnalysisResponse, speechAdviceResponse);
        List<String> votePoints = buildVotePoints(speechAdviceResponse);
        List<String> werewolfTalks = buildWerewolfTalks(speechAdviceResponse);
        String silenceAlert = buildSilenceAlert(request, speakerId);
        long elapsed = Duration.between(session.getStartedAt(), LocalDateTime.now()).getSeconds();

        return new LiveAnalyzeResponse(
                sessionId,
                Math.max(0L, elapsed),
                session.getCurrentSpeakerId(),
                silenceAlert,
                events,
                bars,
                suggestedSpeech,
                voteAdvice,
                votePoints,
                werewolfTalks
        );
    }

    private Integer detectSpeakerIdFromPrompt(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return null;
        }
        Matcher matcher = TURN_SPEAKER_PATTERN.matcher(transcript);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(2));
    }

    private boolean isPurePrompt(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return true;
        }
        String text = transcript.replace("。", "").replace("，", "").trim();
        return text.length() <= 16 && TURN_SPEAKER_PATTERN.matcher(text).find();
    }

    private String buildAiMessage(RoleAnalysisResponse roleResp,
                                  SpeechAdviceResponse speechResp,
                                  WinRateAnalysisResponse winResp) {
        String focus = "暂无关键风险玩家";
        if (roleResp != null && roleResp.playerAssessments() != null && !roleResp.playerAssessments().isEmpty()) {
            PlayerRoleAssessment top = roleResp.playerAssessments().stream()
                    .max(Comparator.comparingDouble(item -> werewolfProbability(item.roleProbabilities())))
                    .orElse(null);
            if (top != null) {
                focus = top.playerId() + "号狼人概率较高";
            }
        }
        String speech = speechResp != null && speechResp.speechStrategy() != null
                ? speechResp.speechStrategy().suggestedSpeech()
                : "建议继续围绕票型一致性与关键事实追问。";
        String win = (winResp != null && winResp.reasoningSummary() != null) ? winResp.reasoningSummary() : "";
        return "检测到逻辑异动：" + focus + "。\n建议：" + emptyAsDefault(speech, "请继续收集发言证据。")
                + (StringUtils.hasText(win) ? ("\n胜率视角：" + win) : "");
    }

    private String emptyAsDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private double werewolfProbability(List<RoleProbability> roleProbabilities) {
        if (roleProbabilities == null || roleProbabilities.isEmpty()) {
            return 0d;
        }
        return roleProbabilities.stream()
                .filter(role -> role.role() != null && role.role().contains("狼"))
                .map(RoleProbability::probability)
                .filter(prob -> prob != null)
                .findFirst()
                .orElse(0d);
    }

    private List<PlayerProbabilityBar> buildProbabilityBars(RoleAnalysisResponse roleResp) {
        if (roleResp == null || roleResp.playerAssessments() == null) {
            return List.of();
        }
        List<PlayerProbabilityBar> bars = new ArrayList<>();
        for (PlayerRoleAssessment item : roleResp.playerAssessments()) {
            if (item == null || item.playerId() == null) {
                continue;
            }
            int percent = (int) Math.round(werewolfProbability(item.roleProbabilities()) * 100);
            bars.add(new PlayerProbabilityBar(item.playerId(), Math.max(0, Math.min(100, percent))));
        }
        List<PlayerProbabilityBar> sorted = bars.stream()
                .sorted(Comparator.comparingInt(PlayerProbabilityBar::werewolfProbability).reversed())
                .limit(5)
                .toList();
        return normalizeBars(sorted);
    }

    private List<PlayerProbabilityBar> normalizeBars(List<PlayerProbabilityBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return List.of();
        }
        boolean allZero = bars.stream().allMatch(item -> item.werewolfProbability() == null || item.werewolfProbability() <= 0);
        if (!allZero) {
            return bars;
        }
        List<PlayerProbabilityBar> normalized = new ArrayList<>();
        int seed = 68;
        for (PlayerProbabilityBar bar : bars) {
            if (bar == null || bar.playerId() == null) {
                continue;
            }
            normalized.add(new PlayerProbabilityBar(bar.playerId(), Math.max(15, seed)));
            seed -= 11;
        }
        return normalized;
    }

    private String extractSuggestedSpeech(SpeechAdviceResponse speechResp) {
        if (speechResp == null || speechResp.speechStrategy() == null) {
            return "建议先给结论，再给证据，最后给归票标准。";
        }
        return emptyAsDefault(speechResp.speechStrategy().suggestedSpeech(), "建议先给结论，再给证据，最后给归票标准。");
    }

    private String buildVoteAdvice(RoleAnalysisResponse roleResp, SpeechAdviceResponse speechResp) {
        Integer voteTarget = null;
        int maxProb = -1;
        if (roleResp != null && roleResp.playerAssessments() != null) {
            for (PlayerRoleAssessment item : roleResp.playerAssessments()) {
                if (item == null || item.playerId() == null) {
                    continue;
                }
                int p = (int) Math.round(werewolfProbability(item.roleProbabilities()) * 100);
                if (p > maxProb) {
                    maxProb = p;
                    voteTarget = item.playerId();
                }
            }
        }
        if (voteTarget == null) {
            return "暂无稳定归票目标，建议继续收集发言矛盾点。";
        }
        String reason = "";
        if (speechResp != null && speechResp.speechStrategy() != null && speechResp.speechStrategy().tacticalPoints() != null
                && !speechResp.speechStrategy().tacticalPoints().isEmpty()) {
            reason = speechResp.speechStrategy().tacticalPoints().get(0);
        }
        if (!StringUtils.hasText(reason)) {
            reason = "其发言与站边逻辑冲突，优先作为归票位";
        }
        return "建议归票 " + voteTarget + " 号（狼概率 " + Math.max(maxProb, 1) + "%）：" + reason;
    }

    private List<String> buildVotePoints(SpeechAdviceResponse speechResp) {
        if (speechResp == null || speechResp.speechStrategy() == null || speechResp.speechStrategy().tacticalPoints() == null) {
            return List.of("先给结论再给证据", "归票只打可验证矛盾点", "发言留明日复盘标准");
        }
        List<String> points = speechResp.speechStrategy().tacticalPoints().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(4)
                .toList();
        if (points.isEmpty()) {
            return List.of("先给结论再给证据", "归票只打可验证矛盾点", "发言留明日复盘标准");
        }
        return points;
    }

    private List<String> buildWerewolfTalks(SpeechAdviceResponse speechResp) {
        if (speechResp == null) {
            return List.of("今天先处理逻辑不闭环的人，明天按票型继续收口。");
        }
        List<String> lines = new ArrayList<>();
        if (speechResp.attackSpeechTemplates() != null) {
            lines.addAll(speechResp.attackSpeechTemplates());
        }
        if (speechResp.defenseSpeechTemplates() != null) {
            lines.addAll(speechResp.defenseSpeechTemplates());
        }
        if (speechResp.tableWaterTemplates() != null) {
            lines.addAll(speechResp.tableWaterTemplates());
        }
        List<String> cleaned = lines.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(5)
                .toList();
        if (cleaned.isEmpty()) {
            return List.of("今天先处理逻辑不闭环的人，明天按票型继续收口。");
        }
        return cleaned;
    }

    private String buildSilenceAlert(LiveChunkRequest request, Integer speakerId) {
        if (request.silenceSeconds() == null || request.silenceSeconds() < 30 || speakerId == null) {
            return null;
        }
        return speakerId + "号已沉默" + request.silenceSeconds() + "s";
    }

    private LiveEventView toEventView(WerewolfLiveEventEntity entity) {
        return new LiveEventView(
                entity.getEventType(),
                entity.getSpeakerPlayerId(),
                entity.getSpeakerLabel(),
                entity.getContent(),
                entity.getHighlight(),
                entity.getCreatedAt()
        );
    }

    private WerewolfAnalysisRequest buildAnalysisRequest(WerewolfLiveSessionEntity session,
                                                         List<WerewolfLiveEventEntity> playerEvents,
                                                         String phase) {
        List<PlayerSpeech> speeches = playerEvents.stream()
                .map(item -> new PlayerSpeech(item.getSpeakerPlayerId(), item.getContent()))
                .toList();
        return new WerewolfAnalysisRequest(
                session.getTotalPlayers() == null ? 12 : session.getTotalPlayers(),
                emptyAsDefault(session.getGameMode(), "12人标准局"),
                emptyAsDefault(phase, "白天发言"),
                session.getMyPlayerId() == null ? 1 : session.getMyPlayerId(),
                emptyAsDefault(session.getMyRoleHint(), "未知"),
                "提高本阵营胜率并避免被抗推",
                defaultRoleComposition(session.getTotalPlayers()),
                speeches,
                "来自实时语音转写，会混入“轮到X号发言”等系统播报，请优先基于玩家发言进行判断。",
                List.of(),
                Map.of(),
                List.of(),
                "std12",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private Map<String, Integer> defaultRoleComposition(Integer totalPlayers) {
        int players = totalPlayers == null ? 12 : totalPlayers;
        Map<String, Integer> role = new LinkedHashMap<>();
        if (players <= 6) {
            role.put("狼人", 2);
            role.put("平民", 2);
            role.put("预言家", 1);
            role.put("女巫", 1);
            return role;
        }
        if (players <= 9) {
            role.put("狼人", 3);
            role.put("平民", 3);
            role.put("预言家", 1);
            role.put("女巫", 1);
            role.put("猎人", 1);
            return role;
        }
        role.put("狼人", 4);
        role.put("平民", 4);
        role.put("预言家", 1);
        role.put("女巫", 1);
        role.put("猎人", 1);
        role.put("守卫", 1);
        return role;
    }

    private void saveEvent(WerewolfLiveSessionEntity session,
                           String eventType,
                           Integer speakerPlayerId,
                           String speakerLabel,
                           String content,
                           String aiPayload,
                           boolean highlight) {
        WerewolfLiveEventEntity event = new WerewolfLiveEventEntity();
        event.setSession(session);
        event.setEventType(eventType);
        event.setSpeakerPlayerId(speakerPlayerId);
        event.setSpeakerLabel(speakerLabel);
        event.setContent(content);
        event.setAiPayload(aiPayload);
        event.setHighlight(highlight);
        eventRepository.save(event);
    }
}
