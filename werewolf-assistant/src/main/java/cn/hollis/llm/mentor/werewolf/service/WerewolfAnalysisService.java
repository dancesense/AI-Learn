package cn.hollis.llm.mentor.werewolf.service;

import cn.hollis.llm.mentor.werewolf.model.PlayerRoleAssessment;
import cn.hollis.llm.mentor.werewolf.model.PlayerSpeech;
import cn.hollis.llm.mentor.werewolf.model.RoleAnalysisResponse;
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
        String prompt = buildSpeechAdvicePrompt(request);
        try {
            return chatClient.prompt(prompt).call().entity(SpeechAdviceResponse.class);
        } catch (Exception ex) {
            return buildFallbackSpeechAdviceResponse(request);
        }
    }

    public RoleAnalysisResponse analyzePlayerRoles(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildRoleAnalysisPrompt(request);
        try {
            RoleAnalysisResponse response = chatClient.prompt(prompt).call().entity(RoleAnalysisResponse.class);
            return enforceMyIdentityForRoleAnalysis(request, response);
        } catch (Exception ex) {
            return buildFallbackRoleAnalysisResponse(request);
        }
    }

    public WinRateAnalysisResponse analyzeWinRates(WerewolfAnalysisRequest request) {
        validateRequest(request);
        String prompt = buildWinRatePrompt(request);
        try {
            return chatClient.prompt(prompt).call().entity(WinRateAnalysisResponse.class);
        } catch (Exception ex) {
            return buildFallbackWinRateAnalysisResponse(request);
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
                  reasoningSummary: string
                }

                要求：
                - suggestedSpeech 是可直接复述的一段中文完整发言。
                - tacticalPoints 给3-5条。
                - forbiddenPoints 给2-4条。
                - 你必须代入“我是%s号，身份是%s”的第一视角，给出以我为主体的决策话术。

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 我是几号：%s
                - 我的身份提示：%s
                - 我的胜利目标：%s
                - 角色构成：%s
                - 额外上下文：%s

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

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 角色构成：%s
                - 常见角色池参考：%s
                - 额外上下文：%s

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

                游戏信息：
                - 总人数：%s
                - 模式：%s
                - 阶段：%s
                - 我是几号：%s
                - 我的身份提示：%s
                - 角色构成：%s
                - 常见角色池参考：%s
                - 额外上下文：%s

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
                "发言建议结构化解析失败，已返回默认稳健话术。"
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

    private void validateRequest(WerewolfAnalysisRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.speeches())) {
            throw new IllegalArgumentException("发言内容不能为空");
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
        if (!hasKnownMyIdentity(request)) {
            return response;
        }
        List<PlayerRoleAssessment> adjusted = enforceMyIdentityForAssessments(request, response.playerAssessments());
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
        if (!hasKnownMyIdentity(request)) {
            return response;
        }
        List<PlayerRoleAssessment> adjusted = enforceMyIdentityForAssessments(request, response.playerAssessments());
        return new RoleAnalysisResponse(
                response.mode(),
                response.phase(),
                adjusted,
                response.reasoningSummary()
        );
    }

    private List<PlayerRoleAssessment> enforceMyIdentityForAssessments(WerewolfAnalysisRequest request,
                                                                        List<PlayerRoleAssessment> assessments) {
        List<PlayerRoleAssessment> adjusted = new ArrayList<>();
        if (assessments != null) {
            adjusted.addAll(assessments);
        }

        Integer myId = request.myPlayerId();
        String myRole = request.myRoleHint().trim();
        PlayerRoleAssessment me = new PlayerRoleAssessment(
                myId,
                myRole,
                1.0,
                List.of(new cn.hollis.llm.mentor.werewolf.model.RoleProbability(myRole, 1.0)),
                List.of("该玩家为我本人，身份已知，按先验信息固定为100%")
        );

        int idx = -1;
        for (int i = 0; i < adjusted.size(); i++) {
            PlayerRoleAssessment cur = adjusted.get(i);
            if (cur != null && myId.equals(cur.playerId())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            adjusted.set(idx, me);
        } else {
            adjusted.add(me);
        }
        return adjusted;
    }

    @Override
    public void afterPropertiesSet() {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultOptions(DashScopeChatOptions.builder().temperature(0.3).build())
                .defaultSystem("你是资深狼人杀教练，擅长概率推演、冲突识别和高胜率发言设计。")
                .build();
    }
}
