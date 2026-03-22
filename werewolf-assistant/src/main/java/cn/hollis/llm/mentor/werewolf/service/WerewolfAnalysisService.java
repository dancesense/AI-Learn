package cn.hollis.llm.mentor.werewolf.service;

import cn.hollis.llm.mentor.werewolf.model.PlayerRoleAssessment;
import cn.hollis.llm.mentor.werewolf.model.PlayerSpeech;
import cn.hollis.llm.mentor.werewolf.model.RoleWinRate;
import cn.hollis.llm.mentor.werewolf.model.SpeechStrategy;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisResponse;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class WerewolfAnalysisService implements InitializingBean {

    private final ChatModel dashScopeChatModel;

    private ChatClient chatClient;

    public WerewolfAnalysisService(ChatModel dashScopeChatModel) {
        this.dashScopeChatModel = dashScopeChatModel;
    }

    public WerewolfAnalysisResponse analyze(WerewolfAnalysisRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.speeches())) {
            throw new IllegalArgumentException("发言内容不能为空");
        }

        String prompt = buildPrompt(request);
        try {
            return chatClient.prompt(prompt).call().entity(WerewolfAnalysisResponse.class);
        } catch (Exception ex) {
            String fallbackSpeech = chatClient.prompt(prompt + "\n请至少输出一段可直接复述的发言，不要解释。")
                    .call().content();
            return buildFallbackResponse(request, fallbackSpeech);
        }
    }

    private String buildPrompt(WerewolfAnalysisRequest request) {
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

                约束：
                - 所有概率需要合理且可比较，不要全给0.5。
                - keyEvidence 必须引用发言中的要点。
                - suggestedSpeech 必须是我可以直接在场上念出来的一段中文话术。
                - tacticalPoints 给3-5条可执行策略，forbiddenPoints 给2-4条禁忌发言。

                游戏信息：
                - 总人数：%d
                - 模式：%s
                - 阶段：%s
                - 我是几号：%s
                - 我的身份提示：%s
                - 我的胜利目标：%s
                - 常见角色池参考：%s
                - 额外上下文：%s

                玩家发言记录：
                %s
                """.formatted(
                request.totalPlayers() == null ? 0 : request.totalPlayers(),
                emptyAsDefault(request.gameMode(), "未指定"),
                emptyAsDefault(request.phase(), "白天发言阶段"),
                request.myPlayerId() == null ? "未指定" : request.myPlayerId(),
                emptyAsDefault(request.myRoleHint(), "未知"),
                emptyAsDefault(request.winningObjective(), "提高自身生存与本阵营胜率"),
                roles,
                emptyAsDefault(request.extraContext(), "无"),
                renderSpeeches(request.speeches())
        );
    }

    private WerewolfAnalysisResponse buildFallbackResponse(WerewolfAnalysisRequest request, String fallbackSpeech) {
        String mode = request.gameMode();
        if (!StringUtils.hasText(mode)) {
            mode = (request.totalPlayers() == null ? "未指定人数场" : request.totalPlayers() + "人场");
        }
        String phase = emptyAsDefault(request.phase(), "白天发言阶段");
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

    @Override
    public void afterPropertiesSet() {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultOptions(DashScopeChatOptions.builder().temperature(0.3).build())
                .defaultSystem("你是资深狼人杀教练，擅长概率推演、冲突识别和高胜率发言设计。")
                .build();
    }
}
