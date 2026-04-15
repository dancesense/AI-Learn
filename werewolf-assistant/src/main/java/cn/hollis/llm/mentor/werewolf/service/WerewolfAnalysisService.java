package cn.hollis.llm.mentor.werewolf.service;

import cn.hollis.llm.mentor.werewolf.model.EmotionGuide;
import cn.hollis.llm.mentor.werewolf.model.LastWordRecord;
import cn.hollis.llm.mentor.werewolf.model.PlayerRoleAssessment;
import cn.hollis.llm.mentor.werewolf.model.PlayerSpeech;
import cn.hollis.llm.mentor.werewolf.model.PostGameReviewResponse;
import cn.hollis.llm.mentor.werewolf.model.PsychologyCoachResponse;
import cn.hollis.llm.mentor.werewolf.model.SkillEvent;
import cn.hollis.llm.mentor.werewolf.model.UserObservedSignal;
import cn.hollis.llm.mentor.werewolf.model.VoteRecord;
import cn.hollis.llm.mentor.werewolf.model.RoleAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.RoleProbability;
import cn.hollis.llm.mentor.werewolf.model.RoleWinRate;
import cn.hollis.llm.mentor.werewolf.model.SpeechAdviceResponse;
import cn.hollis.llm.mentor.werewolf.model.SpeechStrategy;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.WinRateAnalysisResponse;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WerewolfAnalysisService implements InitializingBean {

    private final ChatModel dashScopeChatModel;

    private ChatClient chatClient;

    public WerewolfAnalysisService(ChatModel dashScopeChatModel) {
        this.dashScopeChatModel = dashScopeChatModel;
    }

    public WerewolfAnalysisResponse analyze(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildAllInOnePrompt(request);
        try {
            WerewolfAnalysisResponse response = chatClient.prompt(prompt).call().entity(WerewolfAnalysisResponse.class);
            return enforceMyIdentityForAllInOne(request, response);
        } catch (Exception ex) {
            String fallbackSpeech = chatClient.prompt(prompt + "\n请至少输出一段可直接复述的发言，不要解释。")
                    .call().content();
            return buildFallbackResponse(request, fallbackSpeech);
        }
    }

    public SpeechAdviceResponse analyzeSpeechAdvice(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildSpeechAdvicePrompt(request) + strictOutputGuard("SpeechAdviceResponse");
        try {
            SpeechAdviceResponse raw = chatClient.prompt(prompt).call().entity(SpeechAdviceResponse.class);
            return normalizeSpeechAdvice(raw, request);
        } catch (Exception ex) {
            return buildFallbackSpeechAdviceResponse(request);
        }
    }

    public RoleAnalysisResponse analyzePlayerRoles(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildRoleAnalysisPrompt(request) + strictOutputGuard("RoleAnalysisResponse");
        try {
            RoleAnalysisResponse response = chatClient.prompt(prompt).call().entity(RoleAnalysisResponse.class);
            RoleAnalysisResponse normalized = normalizeRoleAnalysis(response, request);
            return enforceMyIdentityForRoleAnalysis(request, normalized);
        } catch (Exception ex) {
            return buildFallbackRoleAnalysisResponse(request);
        }
    }

    public WinRateAnalysisResponse analyzeWinRates(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildWinRatePrompt(request) + strictOutputGuard("WinRateAnalysisResponse");
        try {
            WinRateAnalysisResponse raw = chatClient.prompt(prompt).call().entity(WinRateAnalysisResponse.class);
            return normalizeWinRateResponse(raw, request);
        } catch (Exception ex) {
            return buildFallbackWinRateAnalysisResponse(request);
        }
    }

    public PsychologyCoachResponse analyzePsychology(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildPsychologyCoachPrompt(request);
        try {
            return chatClient.prompt(prompt).call().entity(PsychologyCoachResponse.class);
        } catch (Exception ex) {
            return buildFallbackPsychologyResponse(request);
        }
    }

    public PostGameReviewResponse analyzePostGame(WerewolfAnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String prompt = buildPostGameReviewPrompt(request);
        try {
            return chatClient.prompt(prompt).call().entity(PostGameReviewResponse.class);
        } catch (Exception ex) {
            return buildFallbackPostGameResponse(request);
        }
    }

    private String buildAllInOnePrompt(WerewolfAnalysisRequest request) {
        String roles = inferRolePool(request.totalPlayers());
        return """
                你是一个狼人杀局势分析助手，请基于提供的游戏信息做结构化分析。
                你必须严格输出JSON，且字段必须完全匹配下面的Java record结构，不要额外字段，不要Markdown：

                WerewolfAnalysisResponse{
                  mode: string,
                  phase: string,
                  playerAssessments: [
                    {
                      playerId: number,
                      likelyRole: string,
                      confidence: number(0-1),
                      roleProbabilities: [{role: string, probability: number(0-1)}],
                      keyEvidence: [string]
                    }
                  ],
                  roleWinRates: [{role: string, winRate: number(0-1)}],
                  speechStrategy: {
                    objective: string,
                    suggestedSpeech: string,
                    tacticalPoints: [string],
                    forbiddenPoints: [string]
                  },
                  reasoningSummary: string
                }

                分析任务：
                1) 基于每个玩家发言，判断其可能角色（例如：狼人、平民、预言家、女巫、猎人等），并给出概率。
                2) 评估当前主要角色/阵营的胜率（0-1）。
                3) 针对“我”的身份与目标，给出高胜率发言话术。
                4) 你必须代入“我是%s号，身份是%s”的视角进行推理和决策，不要把我当作未知身份玩家。

                约束：
                - 所有概率需要合理且可比较，不要全给0.5。
                - keyEvidence 必须引用发言中的要点。
                - suggestedSpeech 必须是我可以直接在场上念出来的一段中文话术。
                - tacticalPoints 给3-5条可执行策略，forbiddenPoints 给2-4条禁忌发言。
                - 如果我的身份已知（不是“未知”），在 playerAssessments 中我自己的身份概率必须为100%%。
                - 已翻牌身份和我已知的狼人同伴，必须按100%%确定身份，不再作为未知概率处理。

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 我是几号：%s
                - 我的身份提示：%s
                - 我的胜利目标：%s
                - 角色构成：%s
                - 常见角色池参考：%s
                - 额外上下文：%s
                - 当前死亡玩家：%s
                - 已翻牌身份：%s
                - 我已知狼人同伴：%s

                扩展局势数据（投票/技能/遗言/观察）：
                %s

                玩家发言记录：
                %s
                """.formatted(
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                String.valueOf(request.totalPlayers() == null ? 0 : request.totalPlayers()),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "白天发言阶段"),
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                emptyAsDefault(request.winningObjective(), "提高自身生存与本阵营胜率"),
                renderRoleComposition(request.roleComposition()),
                roles,
                emptyAsDefault(request.extraContext(), "无"),
                renderDeadPlayers(request.deadPlayers()),
                renderKnownIdentities(request.revealedIdentities()),
                renderKnownWerewolfPlayers(request.knownWerewolfPlayers()),
                renderExtendedSituation(request),
                renderSpeeches(request.speeches())
        );
    }

    private String buildSpeechAdvicePrompt(WerewolfAnalysisRequest request) {
        return """
                你是狼人杀发言策略教练，请严格输出JSON，不要Markdown。
                输出结构必须是：
                SpeechAdviceResponse{
                  mode: string,
                  phase: string,
                  speechStrategy: {
                    objective: string,
                    suggestedSpeech: string,
                    tacticalPoints: [string],
                    forbiddenPoints: [string]
                  },
                  reasoningSummary: string,
                  emotionGuide: {
                    intensity0to100: number,
                    postureSummary: string,
                    actingTips: [string]
                  },
                  defenseSpeechTemplates: [string],
                  attackSpeechTemplates: [string],
                  tableWaterTemplates: [string]
                }

                要求：
                - suggestedSpeech 是可直接复述的一段中文完整发言。
                - tacticalPoints 给3-5条。
                - forbiddenPoints 给2-4条。
                - 你必须代入“我是%s号，身份是%s”的第一视角，给出以我为主体的决策话术。
                - 你必须利用“死亡/翻牌/已知狼人同伴”这些确定信息做决策，不要把它们当作未确认信息。
                - emotionGuide：结合当前阶段与我的身份，给出建议情绪强度(0-100)、一句话仪态总结、3-5条演技/语气指导。
                - defenseSpeechTemplates：2-3条防御型话术模板（被指逻辑矛盾、被悍跳查杀等场景）。
                - attackSpeechTemplates：2-3条进攻/归票型话术模板。
                - tableWaterTemplates：2-3条表水/平民视角模板。

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 我是几号：%s
                - 我的身份提示：%s
                - 我的胜利目标：%s
                - 角色构成：%s
                - 额外上下文：%s
                - 当前死亡玩家：%s
                - 已翻牌身份：%s
                - 我已知狼人同伴：%s

                扩展局势数据（投票/技能/遗言/观察）：
                %s

                玩家发言记录：
                %s
                """.formatted(
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                String.valueOf(request.totalPlayers() == null ? 0 : request.totalPlayers()),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "白天发言阶段"),
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                emptyAsDefault(request.winningObjective(), "提高自身生存与本阵营胜率"),
                renderRoleComposition(request.roleComposition()),
                emptyAsDefault(request.extraContext(), "无"),
                renderDeadPlayers(request.deadPlayers()),
                renderKnownIdentities(request.revealedIdentities()),
                renderKnownWerewolfPlayers(request.knownWerewolfPlayers()),
                renderExtendedSituation(request),
                renderSpeeches(request.speeches())
        );
    }

    private String buildRoleAnalysisPrompt(WerewolfAnalysisRequest request) {
        String roles = inferRolePool(request.totalPlayers());
        return """
                你是狼人杀身份推理助手，请严格输出JSON，不要Markdown。
                输出结构必须是：
                RoleAnalysisResponse{
                  mode: string,
                  phase: string,
                  playerAssessments: [
                    {
                      playerId: number,
                      likelyRole: string,
                      confidence: number(0-1),
                      roleProbabilities: [{role: string, probability: number(0-1)}],
                      keyEvidence: [string]
                    }
                  ],
                  reasoningSummary: string
                }

                要求：
                - 对所有出现在发言记录中的玩家给出评估。
                - roleProbabilities 至少3个角色，且概率有区分度。
                - keyEvidence 必须引用发言中的冲突点、站边点或逻辑链。
                - 你必须代入“我是%s号，身份是%s”的视角进行分析。
                - 如果我的身份已知（不是“未知”），则我这个玩家的 likelyRole 必须是我的身份，confidence=1.0，且对应概率100%%。
                - 已翻牌身份和我已知的狼人同伴必须固定为100%%身份概率。

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 角色构成：%s
                - 常见角色池参考：%s
                - 额外上下文：%s
                - 当前死亡玩家：%s
                - 已翻牌身份：%s
                - 我已知狼人同伴：%s

                扩展局势数据（投票/技能/遗言/观察）：
                %s

                玩家发言记录：
                %s
                """.formatted(
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                String.valueOf(request.totalPlayers() == null ? 0 : request.totalPlayers()),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "白天发言阶段"),
                renderRoleComposition(request.roleComposition()),
                roles,
                emptyAsDefault(request.extraContext(), "无"),
                renderDeadPlayers(request.deadPlayers()),
                renderKnownIdentities(request.revealedIdentities()),
                renderKnownWerewolfPlayers(request.knownWerewolfPlayers()),
                renderExtendedSituation(request),
                renderSpeeches(request.speeches())
        );
    }

    private String buildWinRatePrompt(WerewolfAnalysisRequest request) {
        String roles = inferRolePool(request.totalPlayers());
        return """
                你是狼人杀胜率评估助手，请严格输出JSON，不要Markdown。
                输出结构必须是：
                WinRateAnalysisResponse{
                  mode: string,
                  phase: string,
                  roleWinRates: [{role: string, winRate: number(0-1)}],
                  reasoningSummary: string
                }

                要求：
                - 返回当前关键角色或阵营的胜率（0-1）。
                - 至少包含：好人阵营、狼人阵营，以及场上关键神职角色（如预言家/女巫/猎人）。
                - winRate 必须有区分度，避免全部相同。
                - 你必须代入“我是%s号，身份是%s”的视角来评估当前胜率与风险。
                - 评估时必须利用死亡与翻牌等确定信息，作为硬约束而不是猜测。

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 我是几号：%s
                - 我的身份提示：%s
                - 角色构成：%s
                - 常见角色池参考：%s
                - 额外上下文：%s
                - 当前死亡玩家：%s
                - 已翻牌身份：%s
                - 我已知狼人同伴：%s

                扩展局势数据（投票/技能/遗言/观察）：
                %s

                玩家发言记录：
                %s
                """.formatted(
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                String.valueOf(request.totalPlayers() == null ? 0 : request.totalPlayers()),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "白天发言阶段"),
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                renderRoleComposition(request.roleComposition()),
                roles,
                emptyAsDefault(request.extraContext(), "无"),
                renderDeadPlayers(request.deadPlayers()),
                renderKnownIdentities(request.revealedIdentities()),
                renderKnownWerewolfPlayers(request.knownWerewolfPlayers()),
                renderExtendedSituation(request),
                renderSpeeches(request.speeches())
        );
    }

    private WerewolfAnalysisResponse buildFallbackResponse(WerewolfAnalysisRequest request, String fallbackSpeech) {
        String mode = resolveMode(request);
        String phase = resolvePhase(request);
        SpeechStrategy strategy = new SpeechStrategy(
                "降低被票风险并引导正确出局顺序",
                emptyAsDefault(fallbackSpeech, "我先给出结论：当前最可疑的是发言逻辑前后冲突的人，建议先从其对跳关系和投票动机查验。"),
                List.of("先站住一个可验证视角", "避免情绪化指控", "把矛盾点说成可复盘事实"),
                List.of("不要一次性点满全场狼坑", "不要无证据强打身份")
        );
        return new WerewolfAnalysisResponse(
                mode,
                phase,
                new ArrayList<PlayerRoleAssessment>(),
                List.of(new RoleWinRate("好人阵营", 0.5), new RoleWinRate("狼人阵营", 0.5)),
                strategy,
                "结构化解析失败，已回退为话术建议。"
        );
    }

    private SpeechAdviceResponse buildFallbackSpeechAdviceResponse(WerewolfAnalysisRequest request) {
        SpeechStrategy strategy = new SpeechStrategy(
                "最大化本轮生存并推动可验证信息出现",
                "我先给一个最稳妥的推进思路：今天先处理发言最矛盾、站边最摇摆的位置。我的理由有两点，第一他前后逻辑不闭环，第二他的结论缺少可验证依据。我们先出信息量最低且风险最高的人，明天根据票型和夜间结果再收窄狼坑。",
                List.of("先给结论再给证据", "只打可验证逻辑点", "给出明日复盘标准"),
                List.of("避免情绪化发言", "不要一次点满全场狼坑")
        );
        return new SpeechAdviceResponse(
                resolveMode(request),
                resolvePhase(request),
                strategy,
                "发言建议结构化解析失败，已返回默认稳健话术。",
                new EmotionGuide(55, "语速中等、眼神稳定扫视全场，避免多余小动作。", List.of("语气先稳后狠", "被指认时先复述对方论点再反驳")),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private SpeechAdviceResponse normalizeSpeechAdvice(SpeechAdviceResponse raw, WerewolfAnalysisRequest request) {
        if (raw == null) {
            return buildFallbackSpeechAdviceResponse(request);
        }
        EmotionGuide eg = raw.emotionGuide();
        if (eg == null) {
            eg = new EmotionGuide(60, "根据阶段调整语气强弱，保持可复述的清晰结构。", List.of());
        }
        SpeechStrategy st = raw.speechStrategy();
        if (st == null) {
            st = new SpeechStrategy("稳健推进", "我先整理场上已确认信息与矛盾点，再给出可验证的怀疑对象。", List.of(), List.of());
        }
        return new SpeechAdviceResponse(
                StringUtils.hasText(raw.mode()) ? raw.mode() : resolveMode(request),
                StringUtils.hasText(raw.phase()) ? raw.phase() : resolvePhase(request),
                st,
                raw.reasoningSummary(),
                eg,
                raw.defenseSpeechTemplates() == null ? List.of() : raw.defenseSpeechTemplates(),
                raw.attackSpeechTemplates() == null ? List.of() : raw.attackSpeechTemplates(),
                raw.tableWaterTemplates() == null ? List.of() : raw.tableWaterTemplates()
        );
    }

    private RoleAnalysisResponse normalizeRoleAnalysis(RoleAnalysisResponse raw, WerewolfAnalysisRequest request) {
        if (raw == null) {
            return buildFallbackRoleAnalysisResponse(request);
        }
        List<PlayerRoleAssessment> normalized = new ArrayList<>();
        if (raw.playerAssessments() != null) {
            for (PlayerRoleAssessment item : raw.playerAssessments()) {
                if (item == null || item.playerId() == null) {
                    continue;
                }
                List<RoleProbability> probs = normalizeRoleProbabilities(item.roleProbabilities());
                String likelyRole = StringUtils.hasText(item.likelyRole()) ? item.likelyRole() : (probs.isEmpty() ? "未知" : probs.get(0).role());
                double confidence = clamp01(item.confidence() == null ? 0.5 : item.confidence());
                List<String> evidence = item.keyEvidence() == null ? List.of() : item.keyEvidence().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .limit(4)
                        .toList();
                normalized.add(new PlayerRoleAssessment(item.playerId(), likelyRole, confidence, probs, evidence));
            }
        }
        return new RoleAnalysisResponse(
                StringUtils.hasText(raw.mode()) ? raw.mode() : resolveMode(request),
                StringUtils.hasText(raw.phase()) ? raw.phase() : resolvePhase(request),
                normalized,
                raw.reasoningSummary()
        );
    }

    private List<RoleProbability> normalizeRoleProbabilities(List<RoleProbability> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of(new RoleProbability("未知", 1.0));
        }
        List<RoleProbability> cleaned = raw.stream()
                .filter(item -> item != null && StringUtils.hasText(item.role()) && item.probability() != null)
                .map(item -> new RoleProbability(item.role().trim(), Math.max(0d, item.probability())))
                .limit(6)
                .toList();
        if (cleaned.isEmpty()) {
            return List.of(new RoleProbability("未知", 1.0));
        }
        double sum = cleaned.stream().mapToDouble(RoleProbability::probability).sum();
        if (sum <= 0.0001) {
            double avg = 1d / cleaned.size();
            return cleaned.stream().map(item -> new RoleProbability(item.role(), avg)).toList();
        }
        return cleaned.stream()
                .map(item -> new RoleProbability(item.role(), clamp01(item.probability() / sum)))
                .toList();
    }

    private WinRateAnalysisResponse normalizeWinRateResponse(WinRateAnalysisResponse raw, WerewolfAnalysisRequest request) {
        if (raw == null) {
            return buildFallbackWinRateAnalysisResponse(request);
        }
        List<RoleWinRate> winRates = new ArrayList<>();
        if (raw.roleWinRates() != null) {
            for (RoleWinRate item : raw.roleWinRates()) {
                if (item == null || !StringUtils.hasText(item.role()) || item.winRate() == null) {
                    continue;
                }
                winRates.add(new RoleWinRate(item.role().trim(), clamp01(item.winRate())));
            }
        }
        if (winRates.isEmpty()) {
            winRates = List.of(
                    new RoleWinRate("好人阵营", 0.5),
                    new RoleWinRate("狼人阵营", 0.5)
            );
        }
        return new WinRateAnalysisResponse(
                StringUtils.hasText(raw.mode()) ? raw.mode() : resolveMode(request),
                StringUtils.hasText(raw.phase()) ? raw.phase() : resolvePhase(request),
                winRates,
                raw.reasoningSummary()
        );
    }

    private double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5;
        }
        return Math.max(0d, Math.min(1d, value));
    }

    private String strictOutputGuard(String rootType) {
        return """
                
                【强制输出规范】
                1) 只输出单个 JSON 对象，根对象类型必须是 %s。
                2) 任何概率字段必须是 0~1 的数字，不能是百分号字符串。
                3) 不要输出 Markdown、解释文字、代码块标记。
                4) 列表字段为空时输出 []，不要输出 null。
                """.formatted(rootType);
    }

    private String buildPsychologyCoachPrompt(WerewolfAnalysisRequest request) {
        return """
                你是狼人杀心理博弈教练。请严格输出JSON，不要Markdown。
                输出结构必须是：
                PsychologyCoachResponse{
                  mode: string,
                  phase: string,
                  observationChecklist: [string],
                  pressureQuestions: [string],
                  predictedReactions: [string],
                  counterStrategies: [string],
                  reasoningSummary: string
                }

                任务（模块三：人性推测与压力测试）：
                - observationChecklist：5-8条，教玩家观察哪些微表情/语态信号（停顿、语速、回避问题等），说明要结合现场自行判断。
                - pressureQuestions：4-6条，针对当前最可疑玩家，给出可现场使用的追问/施压问题。
                - predictedReactions：3-5条，预测对方在强压下可能的反应类型（辩解/沉默/转移话题/情绪失控等）。
                - counterStrategies：3-5条，针对上述反应给出应对策略。

                你必须代入“我是%s号，身份是%s”的视角。

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 角色构成：%s
                - 额外上下文：%s
                - 死亡：%s
                - 翻牌：%s
                - 已知狼同伴：%s

                扩展局势数据：
                %s

                玩家发言：
                %s
                """.formatted(
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                String.valueOf(request.totalPlayers() == null ? 0 : request.totalPlayers()),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "白天发言阶段"),
                renderRoleComposition(request.roleComposition()),
                emptyAsDefault(request.extraContext(), "无"),
                renderDeadPlayers(request.deadPlayers()),
                renderKnownIdentities(request.revealedIdentities()),
                renderKnownWerewolfPlayers(request.knownWerewolfPlayers()),
                renderExtendedSituation(request),
                renderSpeeches(request.speeches())
        );
    }

    private String buildPostGameReviewPrompt(WerewolfAnalysisRequest request) {
        return """
                你是狼人杀复盘教练。请严格输出JSON，不要Markdown。
                输出结构必须是：
                PostGameReviewResponse{
                  mode: string,
                  outcomeSummary: string,
                  logicVulnerabilities: [string],
                  emotionVulnerabilities: [string],
                  decisionMistakes: [string],
                  improvementSuggestions: [string],
                  learningResources: [string],
                  reasoningSummary: string
                }

                任务（模块四：赛后复盘）：
                - outcomeSummary：用2-4句话概括本局结果与关键转折（基于提供信息推断即可）。
                - logicVulnerabilities：指出我（%s号）在发言逻辑上的漏洞与矛盾点，3-6条。
                - emotionVulnerabilities：指出可能因情绪/攻击性用语暴露信息的点，2-5条。
                - decisionMistakes：结合投票/技能记录，指出决策失误，3-6条（若无记录则说明信息不足并给通用建议）。
                - improvementSuggestions：可执行的改进建议，5-8条。
                - learningResources：推荐3-5条学习方向（视频/文章/练习方式），用简短条目描述即可。

                游戏信息：
                - 模式：%s
                - 阶段：%s
                - 我的身份提示：%s
                - 对局结果叙述（用户填写）：%s

                扩展局势数据：
                %s

                玩家发言全记录：
                %s
                """.formatted(
                request.myPlayerId() == null ? "?" : request.myPlayerId(),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "赛后复盘"),
                emptyAsDefault(request.myRoleHint(), "未知"),
                emptyAsDefault(request.gameOutcomeNarrative(), "未提供，请根据上下文推断"),
                renderExtendedSituation(request),
                renderSpeeches(request.speeches())
        );
    }

    private PsychologyCoachResponse buildFallbackPsychologyResponse(WerewolfAnalysisRequest request) {
        return new PsychologyCoachResponse(
                resolveMode(request),
                resolvePhase(request),
                List.of(
                        "观察回答关键问题前是否异常停顿（>2秒）",
                        "注意语速突然变化或声音颤抖",
                        "是否回避直接回答而转移话题",
                        "眼神是否稳定，是否频繁看向特定玩家寻求暗示",
                        "复述其观点时是否改口或细节不一致"
                ),
                List.of(
                        "你刚才那句话具体指的是哪一轮、哪个行为？能否按时间线复述一遍？",
                        "你怀疑他的核心依据是什么？如果没有铁逻辑，你为什么愿意为此承担票型风险？",
                        "你声称平民视角，为什么对夜间信息掌握得这么具体？"
                ),
                List.of("强行辩解堆细节", "沉默或敷衍", "反打情绪激怒你", "转移矛盾到另一人"),
                List.of("对方辩解时要求其给出可验证事实；沉默时用封闭式问题逼二选一；被反打时先降温复述再回击"),
                "结构化解析失败，已返回基础心理博弈条目。"
        );
    }

    private PostGameReviewResponse buildFallbackPostGameResponse(WerewolfAnalysisRequest request) {
        return new PostGameReviewResponse(
                resolveMode(request),
                "信息不足，无法生成完整复盘，请先补充投票、技能与结果叙述。",
                List.of(),
                List.of(),
                List.of(),
                List.of("补充每轮投票与夜间技能记录后再复盘", "录下自己的发言回听：删去情绪化句子", "固定一套表水结构：结论-理由-可验证点-明天验票标准"),
                List.of("搜索“狼人杀基础逻辑链教学”", "练习只打一条主线矛盾，避免全场乱打"),
                "复盘结构化解析失败。"
        );
    }

    private RoleAnalysisResponse buildFallbackRoleAnalysisResponse(WerewolfAnalysisRequest request) {
        RoleAnalysisResponse response = new RoleAnalysisResponse(
                resolveMode(request),
                resolvePhase(request),
                new ArrayList<>(),
                "身份概率结构化解析失败，请重试并补充更多发言上下文。"
        );
        return enforceMyIdentityForRoleAnalysis(request, response);
    }

    private WinRateAnalysisResponse buildFallbackWinRateAnalysisResponse(WerewolfAnalysisRequest request) {
        return new WinRateAnalysisResponse(
                resolveMode(request),
                resolvePhase(request),
                List.of(
                        new RoleWinRate("好人阵营", 0.5),
                        new RoleWinRate("狼人阵营", 0.5),
                        new RoleWinRate("预言家", 0.5)
                ),
                "胜率结构化解析失败，已返回基线概率。"
        );
    }

    private String renderSpeeches(List<PlayerSpeech> speeches) {
        if (speeches == null || speeches.isEmpty()) {
            return "（暂无发言）";
        }
        StringBuilder sb = new StringBuilder();
        for (PlayerSpeech speech : speeches) {
            if (speech == null) {
                continue;
            }
            sb.append("玩家")
                    .append(speech.playerId() == null ? "?" : speech.playerId())
                    .append("：")
                    .append(emptyAsDefault(speech.speech(), "（无发言）"))
                    .append("\n");
        }
        return sb.toString();
    }

    private String inferRolePool(Integer totalPlayers) {
        if (totalPlayers == null) {
            return "狼人、平民、预言家、女巫、猎人";
        }
        if (totalPlayers <= 6) {
            return "狼人x2、平民x2、预言家x1、女巫x1";
        }
        if (totalPlayers <= 9) {
            return "狼人x3、平民x3、预言家x1、女巫x1、猎人x1";
        }
        return "狼人x4、平民x4、预言家x1、女巫x1、猎人x1、守卫x1";
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String renderRoleComposition(Map<String, Integer> roleComposition) {
        if (roleComposition == null || roleComposition.isEmpty()) {
            return "未指定";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : roleComposition.entrySet()) {
            parts.add(entry.getKey() + "x" + entry.getValue());
        }
        return String.join("、", parts);
    }

    private String renderDeadPlayers(List<Integer> deadPlayers) {
        if (deadPlayers == null || deadPlayers.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (Integer playerId : deadPlayers) {
            if (playerId != null && playerId > 0) {
                parts.add("玩家" + playerId);
            }
        }
        return parts.isEmpty() ? "无" : String.join("、", parts);
    }

    private String renderKnownWerewolfPlayers(List<Integer> knownWerewolfPlayers) {
        if (knownWerewolfPlayers == null || knownWerewolfPlayers.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (Integer playerId : knownWerewolfPlayers) {
            if (playerId != null && playerId > 0) {
                parts.add("玩家" + playerId);
            }
        }
        return parts.isEmpty() ? "无" : String.join("、", parts);
    }

    private String renderKnownIdentities(Map<Integer, String> revealedIdentities) {
        if (revealedIdentities == null || revealedIdentities.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : revealedIdentities.entrySet()) {
            Integer playerId = entry.getKey();
            String role = entry.getValue();
            if (playerId == null || playerId <= 0 || !StringUtils.hasText(role)) {
                continue;
            }
            parts.add("玩家" + playerId + "=" + role.trim());
        }
        return parts.isEmpty() ? "无" : String.join("、", parts);
    }

    private String renderExtendedSituation(WerewolfAnalysisRequest request) {
        if (request == null) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("板子模板ID：").append(emptyAsDefault(request.boardTemplateId(), "无")).append("\n");
        sb.append("投票记录：").append(renderVoteRecords(request.voteRecords())).append("\n");
        sb.append("技能/夜间事件：").append(renderSkillEvents(request.skillEvents())).append("\n");
        sb.append("遗言：").append(renderLastWords(request.lastWordRecords())).append("\n");
        sb.append("用户观察信号：").append(renderObservedSignals(request.observedSignals()));
        return sb.toString();
    }

    private String renderVoteRecords(List<VoteRecord> votes) {
        if (votes == null || votes.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (VoteRecord v : votes) {
            if (v == null || v.voterId() == null) {
                continue;
            }
            String type = StringUtils.hasText(v.voteType()) ? v.voteType() : "投票";
            String tgt = v.targetId() == null ? "弃票" : ("玩家" + v.targetId());
            parts.add("第" + (v.round() == null ? "?" : v.round()) + "轮/" + type + "：玩家" + v.voterId() + "→" + tgt);
        }
        return parts.isEmpty() ? "无" : String.join("；", parts);
    }

    private String renderSkillEvents(List<SkillEvent> events) {
        if (events == null || events.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (SkillEvent e : events) {
            if (e == null) {
                continue;
            }
            StringBuilder one = new StringBuilder();
            if (e.nightOrDay() != null) {
                one.append("第").append(e.nightOrDay()).append("段/");
            }
            one.append(emptyAsDefault(e.phaseTag(), "事件"));
            one.append("/").append(emptyAsDefault(e.actionType(), "动作"));
            if (e.actorPlayerId() != null) {
                one.append("/行动者玩家").append(e.actorPlayerId());
            }
            if (!CollectionUtils.isEmpty(e.targetPlayerIds())) {
                one.append("/目标").append(e.targetPlayerIds());
            }
            if (StringUtils.hasText(e.details())) {
                one.append("/").append(e.details().trim());
            }
            parts.add(one.toString());
        }
        return String.join("；", parts);
    }

    private String renderLastWords(List<LastWordRecord> lastWords) {
        if (lastWords == null || lastWords.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (LastWordRecord lw : lastWords) {
            if (lw == null || lw.playerId() == null) {
                continue;
            }
            parts.add("玩家" + lw.playerId() + "（第" + (lw.roundOrDay() == null ? "?" : lw.roundOrDay()) + "段）："
                    + emptyAsDefault(lw.content(), "（空）"));
        }
        return parts.isEmpty() ? "无" : String.join(" || ", parts);
    }

    private String renderObservedSignals(List<UserObservedSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        for (UserObservedSignal s : signals) {
            if (s == null || s.playerId() == null) {
                continue;
            }
            parts.add("玩家" + s.playerId() + "[" + emptyAsDefault(s.category(), "观察") + "] "
                    + emptyAsDefault(s.description(), ""));
        }
        return parts.isEmpty() ? "无" : String.join("；", parts);
    }

    private void validateRequest(WerewolfAnalysisRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        boolean hasSpeeches = !CollectionUtils.isEmpty(request.speeches());
        boolean hasContext = StringUtils.hasText(request.extraContext())
                || !CollectionUtils.isEmpty(request.deadPlayers())
                || !CollectionUtils.isEmpty(request.revealedIdentities())
                || !CollectionUtils.isEmpty(request.knownWerewolfPlayers())
                || StringUtils.hasText(request.boardTemplateId())
                || !CollectionUtils.isEmpty(request.voteRecords())
                || !CollectionUtils.isEmpty(request.skillEvents())
                || !CollectionUtils.isEmpty(request.lastWordRecords())
                || !CollectionUtils.isEmpty(request.observedSignals());
        if (!hasSpeeches && !hasContext) {
            throw new IllegalArgumentException("请至少提供发言或局势上下文");
        }
    }

    private String resolveMode(WerewolfAnalysisRequest request) {
        String mode = request.gameMode();
        if (!StringUtils.hasText(mode)) {
            mode = (request.totalPlayers() == null ? "未指定人数场" : request.totalPlayers() + "人场");
        }
        return mode;
    }

    private String resolvePhase(WerewolfAnalysisRequest request) {
        return emptyAsDefault(request.phase(), "白天发言阶段");
    }

    private boolean hasKnownMyIdentity(WerewolfAnalysisRequest request) {
        if (request == null || request.myPlayerId() == null || request.myPlayerId() <= 0) {
            return false;
        }
        if (!StringUtils.hasText(request.myRoleHint())) {
            return false;
        }
        String role = request.myRoleHint().trim();
        return !"未知".equals(role) && !"unknown".equalsIgnoreCase(role) && !"?".equals(role);
    }

    private WerewolfAnalysisResponse enforceMyIdentityForAllInOne(WerewolfAnalysisRequest request, WerewolfAnalysisResponse response) {
        if (response == null) {
            return null;
        }
        List<PlayerRoleAssessment> adjusted = enforceKnownIdentitiesForAssessments(request, response.playerAssessments());
        return new WerewolfAnalysisResponse(
                response.mode(),
                response.phase(),
                adjusted,
                response.roleWinRates(),
                response.speechStrategy(),
                response.reasoningSummary()
        );
    }

    private RoleAnalysisResponse enforceMyIdentityForRoleAnalysis(WerewolfAnalysisRequest request, RoleAnalysisResponse response) {
        if (response == null) {
            return null;
        }
        List<PlayerRoleAssessment> adjusted = enforceKnownIdentitiesForAssessments(request, response.playerAssessments());
        return new RoleAnalysisResponse(
                response.mode(),
                response.phase(),
                adjusted,
                response.reasoningSummary()
        );
    }

    private List<PlayerRoleAssessment> enforceKnownIdentitiesForAssessments(WerewolfAnalysisRequest request,
                                                                             List<PlayerRoleAssessment> assessments) {
        Map<Integer, String> knownIdentities = buildKnownIdentities(request);
        if (knownIdentities.isEmpty()) {
            return assessments == null ? new ArrayList<>() : assessments;
        }
        List<PlayerRoleAssessment> adjusted = new ArrayList<>();
        if (assessments != null) {
            adjusted.addAll(assessments);
        }
        for (Map.Entry<Integer, String> entry : knownIdentities.entrySet()) {
            Integer playerId = entry.getKey();
            String role = entry.getValue();
            PlayerRoleAssessment fixed = new PlayerRoleAssessment(
                    playerId,
                    role,
                    1.0,
                    List.of(new RoleProbability(role, 1.0)),
                    List.of("该玩家身份为已知信息，按先验约束固定为100%")
            );
            int idx = -1;
            for (int i = 0; i < adjusted.size(); i++) {
                PlayerRoleAssessment cur = adjusted.get(i);
                if (cur != null && playerId.equals(cur.playerId())) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                adjusted.set(idx, fixed);
            } else {
                adjusted.add(fixed);
            }
        }
        return adjusted;
    }

    private Map<Integer, String> buildKnownIdentities(WerewolfAnalysisRequest request) {
        Map<Integer, String> known = new LinkedHashMap<>();
        if (request == null) {
            return known;
        }
        if (request.revealedIdentities() != null) {
            for (Map.Entry<Integer, String> entry : request.revealedIdentities().entrySet()) {
                Integer playerId = entry.getKey();
                String role = entry.getValue();
                if (playerId == null || playerId <= 0 || !StringUtils.hasText(role)) {
                    continue;
                }
                known.put(playerId, role.trim());
            }
        }
        if (request.knownWerewolfPlayers() != null) {
            for (Integer playerId : request.knownWerewolfPlayers()) {
                if (playerId != null && playerId > 0) {
                    known.put(playerId, "狼人");
                }
            }
        }
        if (hasKnownMyIdentity(request)) {
            known.put(request.myPlayerId(), request.myRoleHint().trim());
        }
        return known;
    }

    @Override
    public void afterPropertiesSet() {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultOptions(DashScopeChatOptions.builder().temperature(0.3).build())
                .defaultSystem("你是资深狼人杀教练，擅长概率推演、冲突识别和高胜率发言设计。")
                .build();
    }
}
