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
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

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
public class WerewolfLiveAnalysisService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(WerewolfLiveAnalysisService.class);

    private static final Pattern TURN_SPEAKER_PATTERN = Pattern.compile("(轮到)?\\s*(\\d+)\\s*号(?:玩家)?发言");

    private final WerewolfLiveSessionRepository sessionRepository;
    private final WerewolfLiveEventRepository eventRepository;
    private final WerewolfAnalysisService analysisService;
    private final ChatModel chatModel;
    private ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WerewolfLiveAnalysisService(WerewolfLiveSessionRepository sessionRepository,
                                       WerewolfLiveEventRepository eventRepository,
                                       WerewolfAnalysisService analysisService,
                                       ChatModel chatModel) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.analysisService = analysisService;
        this.chatModel = chatModel;
        // 确保JSON输出非ASCII字符时不转义
        this.objectMapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
    }

    @Override
    public void afterPropertiesSet() {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(DashScopeChatOptions.builder().temperature(0.3).build())
                .defaultSystem("你是资深狼人杀教练，擅长概率推演、冲突识别和高胜率发言设计。")
                .build();
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

    /**
     * 仅保存发言文本，不触发AI分析（轻量接口）
     */
    @Transactional
    public void saveSpeech(Long sessionId, LiveChunkRequest request) {
        WerewolfLiveSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("实时会话不存在: " + sessionId));

        if (request != null && StringUtils.hasText(request.myRoleHint())) {
            session.setMyRoleHint(request.myRoleHint().trim());
        }

        String transcript = request.transcript() == null ? "" : request.transcript().trim();
        Integer speakerId = request.speakerPlayerId();
        Integer day = request.day() == null || request.day() < 1 ? 1 : request.day();

        if (StringUtils.hasText(transcript)) {
            String speakerLabel = speakerId == null ? "未知玩家" : speakerId + "号";
            saveEvent(session, day, "PLAYER_SPEECH", speakerId, speakerLabel, transcript, null, false);
        }

        sessionRepository.save(session);
    }

    @Transactional
    public LiveAnalyzeResponse consumeChunk(Long sessionId, LiveChunkRequest request) {
        WerewolfLiveSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("实时会话不存在: " + sessionId));

        if (request != null && StringUtils.hasText(request.myRoleHint())) {
            session.setMyRoleHint(request.myRoleHint().trim());
        }

        String transcript = request.transcript() == null ? "" : request.transcript().trim();
        Integer detectedSpeakerId = detectSpeakerIdFromPrompt(transcript);
        if (detectedSpeakerId != null) {
            session.setCurrentSpeakerId(detectedSpeakerId);
            saveEvent(session, 1, "SYSTEM_PROMPT", detectedSpeakerId, detectedSpeakerId + "号", transcript, null, false);
        }

        boolean purePrompt = isPurePrompt(transcript);
        Integer speakerId = request.speakerPlayerId() != null
                ? request.speakerPlayerId()
                : (detectedSpeakerId != null ? detectedSpeakerId : session.getCurrentSpeakerId());

        if (StringUtils.hasText(transcript) && !purePrompt) {
            String speakerLabel = speakerId == null ? "未知玩家" : speakerId + "号";
            saveEvent(session, 1, "PLAYER_SPEECH", speakerId, speakerLabel, transcript, null, false);
        }

        List<WerewolfLiveEventEntity> playerEvents = eventRepository
                .findBySession_IdAndDayAndEventTypeOrderByCreatedAtAsc(sessionId, 1, "PLAYER_SPEECH");

        RoleAnalysisResponse roleAnalysisResponse = null;
        SpeechAdviceResponse speechAdviceResponse = null;
        WinRateAnalysisResponse winRateAnalysisResponse = null;

        if (!playerEvents.isEmpty()) {
            WerewolfAnalysisRequest aiRequest = buildAnalysisRequest(session, playerEvents, request.phase());
            roleAnalysisResponse = analysisService.analyzePlayerRoles(aiRequest);
            speechAdviceResponse = analysisService.analyzeSpeechAdvice(aiRequest);
            winRateAnalysisResponse = analysisService.analyzeWinRates(aiRequest);
            String aiMessage = buildAiMessage(roleAnalysisResponse, speechAdviceResponse, winRateAnalysisResponse);
            saveEvent(session, 1, "AI_INSIGHT", null, "AI", aiMessage, null, true);
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
                roleAnalysisResponse == null || roleAnalysisResponse.playerAssessments() == null
                        ? List.of()
                        : roleAnalysisResponse.playerAssessments(),
                suggestedSpeech,
                voteAdvice,
                votePoints,
                werewolfTalks
        );
    }

    /**
     * 流式分析：先快速流式输出AI分析文字，再返回概率等结构化数据
     * @param sender SSE事件发送回调 (eventName, jsonData) -> void
     */
    @Transactional
    public void consumeChunkStreaming(Long sessionId, LiveChunkRequest request, java.util.function.BiConsumer<String, String> sender) {
        WerewolfLiveSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("实时会话不存在: " + sessionId));

        if (request != null && StringUtils.hasText(request.myRoleHint())) {
            session.setMyRoleHint(request.myRoleHint().trim());
        }

        Integer day = request.day() == null || request.day() < 1 ? 1 : request.day();
        int totalPlayers = request.totalPlayers() != null && request.totalPlayers() > 0
                ? request.totalPlayers()
                : (session.getTotalPlayers() != null ? session.getTotalPlayers() : 12);

        String transcript = request.transcript() == null ? "" : request.transcript().trim();
        Integer detectedSpeakerId = detectSpeakerIdFromPrompt(transcript);
        if (detectedSpeakerId != null) {
            session.setCurrentSpeakerId(detectedSpeakerId);
            saveEvent(session, day, "SYSTEM_PROMPT", detectedSpeakerId, detectedSpeakerId + "号", transcript, null, false);
        }

        boolean purePrompt = isPurePrompt(transcript);
        Integer speakerId = request.speakerPlayerId() != null
                ? request.speakerPlayerId()
                : (detectedSpeakerId != null ? detectedSpeakerId : session.getCurrentSpeakerId());

        if (StringUtils.hasText(transcript) && !purePrompt) {
            String speakerLabel = speakerId == null ? "未知玩家" : speakerId + "号";
            saveEvent(session, day, "PLAYER_SPEECH", speakerId, speakerLabel, transcript, null, false);
        }

        sessionRepository.save(session);

        String analysisType = request.analysisType() == null ? "roundSummary" : request.analysisType();

        // 构建前几天摘要（从数据库查询）
        String previousDaysSummary = buildPreviousDaysSummaryFromDb(sessionId, day);
        // 如果前端传了更详细的 previousDaysSummary，优先使用
        if (StringUtils.hasText(request.previousDaysSummary())) {
            previousDaysSummary = request.previousDaysSummary();
        }

        // ===== Phase 1: 流式快速分析文字 =====
        String fullAnalysisText = "";
        try {
            List<WerewolfLiveEventEntity> playerEvents = eventRepository
                    .findBySession_IdAndDayAndEventTypeOrderByCreatedAtAsc(sessionId, day, "PLAYER_SPEECH");

            if (!playerEvents.isEmpty() || "dataPanel".equals(analysisType)) {
                String quickPrompt = buildQuickAnalysisPrompt(
                        session, playerEvents, request.phase(), analysisType,
                        request, previousDaysSummary, totalPlayers);
                StringBuilder sb = new StringBuilder();

                chatClient.prompt(quickPrompt).stream().content()
                        .doOnNext(chunk -> {
                            try {
                                String json = objectMapper.writeValueAsString(Map.of("content", chunk));
                                sender.accept("text", json);
                            } catch (Exception e) {
                                log.warn("[stream] send text chunk error", e);
                            }
                        })
                        .doOnComplete(() -> {
                        })
                        .doOnError(e -> log.warn("[stream] text stream error", e))
                        .toStream()
                        .forEach(sb::append);

                fullAnalysisText = sb.toString().trim();
            }
        } catch (Exception ex) {
            log.error("[stream] Phase1 quick analysis failed", ex);
            fullAnalysisText = "AI分析暂时不可用，请等待结构化分析结果。";
        }

        // 保存AI分析到事件表
        if (StringUtils.hasText(fullAnalysisText)) {
            saveEvent(session, day, "AI_INSIGHT", null, "AI", fullAnalysisText, null, true);
        }

        // 从流式输出中解析概率数据（dataPanel专用）
        List<PlayerRoleAssessment> dataPanelAssessments = new ArrayList<>();
        if ("dataPanel".equals(analysisType)) {
            dataPanelAssessments = parseProbabilitiesFromText(fullAnalysisText, totalPlayers);
            log.info("[stream] dataPanel parsed {} assessments", dataPanelAssessments.size());
        }

        // ===== Phase 2: 结构化分析（概率、角色、胜率）—— 仅当天 =====
        LiveAnalyzeResponse fullResponse = null;
        try {
            List<WerewolfLiveEventEntity> allEvents = eventRepository
                    .findBySession_IdAndDayAndEventTypeOrderByCreatedAtAsc(sessionId, day, "PLAYER_SPEECH");

            if (!allEvents.isEmpty()) {
                WerewolfAnalysisRequest aiRequest = buildAnalysisRequest(session, allEvents, request.phase());
                RoleAnalysisResponse roleResp = analysisService.analyzePlayerRoles(aiRequest);
                SpeechAdviceResponse speechResp = analysisService.analyzeSpeechAdvice(aiRequest);
                WinRateAnalysisResponse winResp = analysisService.analyzeWinRates(aiRequest);

                List<LiveEventView> events = eventRepository.findBySession_IdAndDayOrderByCreatedAtAsc(sessionId, day).stream()
                        .map(this::toEventView)
                        .toList();

                List<PlayerProbabilityBar> bars = buildProbabilityBars(roleResp);
                String suggestedSpeech = extractSuggestedSpeech(speechResp);
                String voteAdvice = buildVoteAdvice(roleResp, speechResp);
                List<String> votePoints = buildVotePoints(speechResp);
                List<String> werewolfTalks = buildWerewolfTalks(speechResp);
                long elapsed = Duration.between(session.getStartedAt(), LocalDateTime.now()).getSeconds();

                // 用流式分析文本覆盖 summary
                fullResponse = new LiveAnalyzeResponse(
                        sessionId,
                        Math.max(0L, elapsed),
                        session.getCurrentSpeakerId(),
                        buildSilenceAlert(request, speakerId),
                        events,
                        bars,
                        roleResp == null || roleResp.playerAssessments() == null
                                ? List.of()
                                : roleResp.playerAssessments(),
                        suggestedSpeech,
                        voteAdvice,
                        votePoints,
                        werewolfTalks
                );
            }
        } catch (Exception ex) {
            log.error("[stream] Phase2 structured analysis failed", ex);
        }

        // ===== Phase 3: 发送最终结构化数据 =====
        try {
            if ("dataPanel".equals(analysisType) && !dataPanelAssessments.isEmpty()) {
                // dataPanel类型：使用从流式输出中解析的概率数据构造响应
                List<PlayerProbabilityBar> bars = new ArrayList<>();
                for (PlayerRoleAssessment item : dataPanelAssessments) {
                    if (item == null || item.playerId() == null) continue;
                    double wolfProb = 0;
                    if (item.roleProbabilities() != null) {
                        for (RoleProbability rp : item.roleProbabilities()) {
                            if (rp.role() != null && rp.role().contains("狼") && rp.probability() != null) {
                                wolfProb = rp.probability();
                                break;
                            }
                        }
                    }
                    bars.add(new PlayerProbabilityBar(item.playerId(), (int) Math.round(wolfProb * 100)));
                }
                long elapsed = Duration.between(session.getStartedAt(), LocalDateTime.now()).getSeconds();
                LiveAnalyzeResponse dataPanelResponse = new LiveAnalyzeResponse(
                        sessionId,
                        Math.max(0L, elapsed),
                        session.getCurrentSpeakerId(),
                        null,
                        List.of(),
                        bars,
                        dataPanelAssessments,
                        "",
                        "",
                        List.of(),
                        List.of()
                );
                String json = objectMapper.writeValueAsString(dataPanelResponse);
                sender.accept("complete", json);
            } else if (fullResponse != null) {
                String json = objectMapper.writeValueAsString(fullResponse);
                sender.accept("complete", json);
            }
            sender.accept("done", "[DONE]");
        } catch (Exception ex) {
            log.error("[stream] send complete event error", ex);
        }
    }

    /**
     * 从数据库构建前几天的发言摘要
     */
    private String buildPreviousDaysSummaryFromDb(Long sessionId, Integer currentDay) {
        if (currentDay == null || currentDay <= 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int d = 1; d < currentDay; d++) {
            List<WerewolfLiveEventEntity> dayEvents = eventRepository
                    .findBySession_IdAndDayAndEventTypeOrderByCreatedAtAsc(sessionId, d, "PLAYER_SPEECH");
            if (dayEvents.isEmpty()) continue;
            sb.append("\n=== 第").append(d).append("天发言 ===\n");
            for (WerewolfLiveEventEntity e : dayEvents) {
                sb.append(e.getSpeakerLabel()).append("：").append(e.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 构建数据面板上下文文本
     */
    private String buildDataPanelContext(LiveChunkRequest request, int totalPlayers) {
        StringBuilder sb = new StringBuilder();

        // 存活状态
        if (request.playerAlive() != null && !request.playerAlive().isEmpty()) {
            sb.append("\n【存活状态】\n");
            List<String> aliveList = new ArrayList<>();
            List<String> deadList = new ArrayList<>();
            for (int i = 1; i <= totalPlayers; i++) {
                Boolean alive = request.playerAlive().get(i);
                if (alive != null && !alive) {
                    deadList.add(i + "号(已出局)");
                } else {
                    aliveList.add(i + "号");
                }
            }
            if (!deadList.isEmpty()) {
                sb.append("已出局：").append(String.join("、", deadList)).append("\n");
            }
            sb.append("存活：").append(String.join("、", aliveList)).append("\n");
        }

        // 已知身份
        if (request.playerRoles() != null && !request.playerRoles().isEmpty()) {
            sb.append("\n【已知身份】\n");
            request.playerRoles().forEach((pid, role) -> {
                if (StringUtils.hasText(role)) {
                    sb.append(pid).append("号 → ").append(role).append("\n");
                }
            });
        }

        // 技能记录
        if (request.skillLogs() != null && !request.skillLogs().isEmpty()) {
            sb.append("\n【神职技能记录】\n");
            for (String log : request.skillLogs()) {
                sb.append("· ").append(log).append("\n");
            }
        }

        // 投票记录
        if (request.voteRecords() != null && !request.voteRecords().isEmpty()) {
            sb.append("\n【投票记录】\n");
            Map<Integer, List<String>> byDay = new LinkedHashMap<>();
            for (Map<String, Object> vr : request.voteRecords()) {
                Object dayObj = vr.get("day");
                int day = dayObj instanceof Number ? ((Number) dayObj).intValue() : 1;
                Object fromObj = vr.get("from");
                Object toObj = vr.get("to");
                String from = fromObj != null ? fromObj.toString() : "?";
                String to = toObj != null ? toObj.toString() : "?";
                byDay.computeIfAbsent(day, k -> new ArrayList<>())
                        .add(from + "号 → " + to + "号");
            }
            byDay.forEach((day, votes) -> {
                sb.append("第").append(day).append("天：").append(String.join("，", votes)).append("\n");
            });
        }

        return sb.toString();
    }

    /**
     * 构建快速分析 prompt —— 输出自然语言，适合流式
     */
    private String buildQuickAnalysisPrompt(WerewolfLiveSessionEntity session,
                                            List<WerewolfLiveEventEntity> playerEvents,
                                            String phase,
                                            String analysisType,
                                            LiveChunkRequest request,
                                            String previousDaysSummary,
                                            int totalPlayers) {
        StringBuilder speeches = new StringBuilder();
        for (WerewolfLiveEventEntity e : playerEvents) {
            speeches.append(e.getSpeakerLabel()).append("：").append(e.getContent()).append("\n");
        }
        String gameMode = emptyAsDefault(session.getGameMode(), "12人标准局");
        String gamePhase = emptyAsDefault(phase, "白天发言");
        String mySeat = session.getMyPlayerId() == null ? "未指定" : String.valueOf(session.getMyPlayerId());
        String myRole = emptyAsDefault(session.getMyRoleHint(), "未知");
        String speechRecord = speeches.toString().isBlank() ? "（暂无发言）" : speeches.toString();

        // 身份推断约束
        String identityConstraint = """
                【身份推断严格约束 - 必须遵守】
                1. 绝对禁止在没有直接证据（如：直接报查验结果、悍跳对跳、明确自曝等）的情况下断言任何人的具体身份。
                2. 如果某玩家只说了"跳过""没信息""过"等无关内容，其身份必须标注为"身份未知/无法判断"。
                3. 不得根据玩家位置（如"2号是预言家位"）推断身份，位置与身份无关。
                4. 所有身份判断必须基于发言中的直接事实证据，并在给出判断时同时说明依据。
                5. 对不确定的人，使用"可能""倾向"等不确定措辞，禁止使用"是""就是""肯定是"等确定性措辞。
                """;

        // 数据面板信息
        String dataPanelInfo = buildDataPanelContext(request, totalPlayers);

        // 前几天摘要
        String previousDaysInfo = StringUtils.hasText(previousDaysSummary)
                ? "\n【前几天局势回顾】\n" + previousDaysSummary + "\n"
                : "";

        if ("speechAdvice".equals(analysisType)) {
            return """
                    你是一个普通狼人杀玩家，正在帮我（%s号，身份：%s）想一段发言。
                    请根据场上已有发言，帮我设计一段自然、像真人说的话。

                    %s

                    要求：
                    1. 话术必须像真人说话，用口语、有停顿、有犹豫，不要太流畅太完美
                    2. 不要用"综上所述""由此可见""逻辑闭环"这种书面/专业词汇
                    3. 适当留有破绽和模糊空间，不要每句话都精准到位，真人做不到
                    4. 长度80-150字，不要长篇大论
                    5. 不要JSON，不要Markdown，不要编号列表
                    6. 如果信息不够，可以说"我还没看明白"之类的，不要强行分析

                    当前情况：
                    - 模式：%s
                    - 阶段：%s
                    - 总人数：%d人

                    %s
                    %s
                    玩家发言记录：
                    %s
                    """.formatted(mySeat, myRole, identityConstraint, gameMode, gamePhase, totalPlayers,
                    dataPanelInfo, previousDaysInfo, speechRecord);
        }

        if ("roleAnalysis".equals(analysisType)) {
            return """
                    你是资深狼人杀教练。请根据场上发言和数据面板信息，重点分析每个玩家的角色概率。

                    %s

                    要求：
                    1. 分析控制在150字以内，简洁明了
                    2. 对每个已发言玩家给出简短角色判断，未发言的标"身份未知"
                    3. 指出关键矛盾点
                    4. 不要JSON，不要Markdown，不要列表符号

                    当前情况：
                    - 模式：%s
                    - 阶段：%s
                    - 我是%s号，身份：%s
                    - 总人数：%d人

                    %s
                    %s
                    玩家发言记录：
                    %s
                    """.formatted(identityConstraint, gameMode, gamePhase, mySeat, myRole, totalPlayers,
                    dataPanelInfo, previousDaysInfo, speechRecord);
        }

        if ("voteAdvice".equals(analysisType)) {
            return """
                    你是资深狼人杀教练。当前所有玩家发言完毕，进入投票阶段。
                    请根据场上所有发言和数据面板信息，给出明确的投票建议。

                    %s

                    要求：
                    1. 分析控制在150字以内，简洁明了
                    2. 给出最应该投给谁、简要理由（基于直接证据）
                    3. 如果有多个人可选，给出优先级排序
                    4. 不要JSON，不要Markdown，不要列表符号

                    当前情况：
                    - 模式：%s
                    - 阶段：%s
                    - 我是%s号，身份：%s
                    - 总人数：%d人

                    %s
                    %s
                    玩家发言记录：
                    %s
                    """.formatted(identityConstraint, gameMode, gamePhase, mySeat, myRole, totalPlayers,
                    dataPanelInfo, previousDaysInfo, speechRecord);
        }

        if ("dataPanel".equals(analysisType)) {
            return """
                    你是资深狼人杀教练。请根据数据面板信息做简短分析并给出角色概率。

                    %s

                    要求：
                    1. 分析控制在100字以内，简洁明了
                    2. 对存活玩家给出角色概率估算
                    3. 概率格式必须如下（每行一个玩家）：
                       【概率数据】
                       1号:狼人35% 平民40% 预言家10% 女巫5% 猎人5% 守卫5%
                       2号:狼人30% 平民45% 预言家5% 女巫10% 猎人5% 守卫5%
                    4. 已出局玩家概率全部标为0%
                    5. 不要JSON，不要Markdown

                    当前情况：
                    - 模式：%s
                    - 阶段：%s
                    - 我是%s号，身份：%s
                    - 总人数：%d人

                    %s
                    %s
                    玩家发言记录：
                    %s
                    """.formatted(identityConstraint, gameMode, gamePhase, mySeat, myRole, totalPlayers,
                    dataPanelInfo, previousDaysInfo, speechRecord);
        }

        // roundSummary 默认
        return """
                你是资深狼人杀教练。请根据场上发言和数据面板信息，给出本轮的整体总结分析。

                %s

                要求：
                1. 分析控制在150字以内，简洁明了
                2. 指出场上关键矛盾和逻辑冲突
                3. 对发言过的人给出简短可疑度判断，未发言的标"身份未知"
                4. 给出归票建议
                5. 不要JSON，不要Markdown，不要列表符号

                当前情况：
                - 模式：%s
                - 阶段：%s
                - 我是%s号，身份：%s
                - 总人数：%d人

                %s
                %s
                玩家发言记录：
                %s
                """.formatted(identityConstraint, gameMode, gamePhase, mySeat, myRole, totalPlayers,
                dataPanelInfo, previousDaysInfo, speechRecord);
    }

    /**
     * 从AI流式输出文本中解析概率数据（dataPanel专用）
     */
    private List<PlayerRoleAssessment> parseProbabilitiesFromText(String text, int totalPlayers) {
        List<PlayerRoleAssessment> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) return result;

        int startIdx = text.indexOf("【概率数据】");
        if (startIdx < 0) return result;

        String probSection = text.substring(startIdx + 6); // 跳过"【概率数据】"
        String[] lines = probSection.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!line.contains("号")) continue;
            if (line.contains("【")) break; // 遇到下一个标记结束

            Pattern playerPattern = Pattern.compile("(\\d+)号[:：]\\s*(.+)");
            Matcher playerMatcher = playerPattern.matcher(line);
            if (!playerMatcher.find()) continue;

            int playerId = Integer.parseInt(playerMatcher.group(1));
            String probsStr = playerMatcher.group(2);

            List<RoleProbability> roleProbs = new ArrayList<>();
            Pattern rolePattern = Pattern.compile("(狼人|平民|预言家|女巫|猎人|守卫|狼|村民|神职)(\\d+)%");
            Matcher roleMatcher = rolePattern.matcher(probsStr);
            while (roleMatcher.find()) {
                String roleName = roleMatcher.group(1);
                int percent = Integer.parseInt(roleMatcher.group(2));
                if ("狼".equals(roleName)) roleName = "狼人";
                if ("村民".equals(roleName)) roleName = "平民";
                roleProbs.add(new RoleProbability(roleName, percent / 100.0));
            }

            if (!roleProbs.isEmpty()) {
                result.add(new PlayerRoleAssessment(playerId, null, null, roleProbs, null));
            }
        }

        return result;
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
        String focus = "暂未识别出高风险玩家";
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
                : "建议继续围绕票型一致性和关键事实追问。";
        String win = (winResp != null && winResp.reasoningSummary() != null) ? winResp.reasoningSummary() : "";
        return "检测到局势变化: " + focus + "。\n建议: " + emptyAsDefault(speech, "请继续收集发言证据。")
                + (StringUtils.hasText(win) ? ("\n胜率视角: " + win) : "");
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
        return "建议归票 " + voteTarget + " 号（狼概率 " + Math.max(maxProb, 1) + "%）: " + reason;
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
                "来自实时语音转写，可能混入系统播报，请优先基于玩家真实发言判断。严格禁止在没有明确证据的情况下断言任何人的具体身份。",
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
                           Integer day,
                           String eventType,
                           Integer speakerPlayerId,
                           String speakerLabel,
                           String content,
                           String aiPayload,
                           boolean highlight) {
        WerewolfLiveEventEntity event = new WerewolfLiveEventEntity();
        event.setSession(session);
        event.setDay(day != null && day >= 1 ? day : 1);
        event.setEventType(eventType);
        event.setSpeakerPlayerId(speakerPlayerId);
        event.setSpeakerLabel(speakerLabel);
        event.setContent(content);
        event.setAiPayload(aiPayload);
        event.setHighlight(highlight);
        eventRepository.save(event);
    }

    public java.util.Map<String, Object> getSessionReview(Long sessionId) {
        WerewolfLiveSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("实时会话不存在: " + sessionId));

        List<WerewolfLiveEventEntity> events = eventRepository
                .findTop80BySession_IdOrderByCreatedAtDesc(sessionId).stream()
                .sorted(Comparator.comparing(WerewolfLiveEventEntity::getCreatedAt))
                .toList();

        List<LiveEventView> eventViews = events.stream()
                .map(this::toEventView)
                .toList();

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", session.getId());
        result.put("gameMode", session.getGameMode());
        result.put("totalPlayers", session.getTotalPlayers());
        result.put("myRoleHint", session.getMyRoleHint());
        result.put("status", session.getStatus());
        result.put("startedAt", session.getStartedAt());
        result.put("events", eventViews);
        return result;
    }

    /**
     * 获取指定天的发言记录（按玩家分组）
     */
    public java.util.Map<String, Object> getSpeechesByDay(Long sessionId, Integer day) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("实时会话不存在: " + sessionId));
        Integer safeDay = day == null || day < 1 ? 1 : day;

        List<WerewolfLiveEventEntity> events = eventRepository
                .findBySession_IdAndDayAndEventTypeOrderByCreatedAtAsc(sessionId, safeDay, "PLAYER_SPEECH");

        // 按玩家分组 {playerId: [content1, content2, ...]}
        java.util.Map<Integer, List<String>> grouped = new java.util.LinkedHashMap<>();
        for (WerewolfLiveEventEntity e : events) {
            Integer pid = e.getSpeakerPlayerId();
            if (pid == null) pid = 0;
            grouped.computeIfAbsent(pid, k -> new ArrayList<>()).add(e.getContent());
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("day", safeDay);
        result.put("speeches", grouped);
        return result;
    }
}
