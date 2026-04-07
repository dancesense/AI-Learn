package cn.hollis.llm.mentor.werewolf.service;

import cn.hollis.llm.mentor.werewolf.model.AdvancedTermCard;
import cn.hollis.llm.mentor.werewolf.model.AdvancedTermsResponse;
import cn.hollis.llm.mentor.werewolf.model.GestureCard;
import cn.hollis.llm.mentor.werewolf.model.GestureTeachingResponse;
import cn.hollis.llm.mentor.werewolf.model.GrowthPlanResponse;
import cn.hollis.llm.mentor.werewolf.model.PlayerSpeech;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class WerewolfLearningService {

    private static final List<AdvancedTermCard> TERM_LIBRARY = List.of(
            new AdvancedTermCard("警徽流", "策略术语", "C", "预言家提前规划次日查验顺序。", "警上对跳后稳定团队预期", "我今天留警徽流，先验5再验8。"),
            new AdvancedTermCard("站边", "逻辑术语", "C", "明确支持哪位核心发言者（通常是对跳预言家之一）。", "警下归票前统一视角", "当前我站边3号真预。"),
            new AdvancedTermCard("表水", "发言术语", "C", "闭眼位自证视角、降低被抗推概率。", "被点进狼坑时自保", "我是民牌，按轮次给你们复盘我的票型。"),
            new AdvancedTermCard("抗推位", "局势术语", "C", "容易被放逐、信息价值低的位置。", "白天归票选择", "今天先出抗推位拿信息。"),
            new AdvancedTermCard("冲锋狼", "身份术语", "B", "正面带节奏、高攻击性的狼人。", "前中期抢票权", "这个位置像冲锋狼，火力太刻意。"),
            new AdvancedTermCard("倒钩狼", "身份术语", "B", "表面站边真预，实则隐藏狼身份。", "中后期隐蔽存活", "他连续两轮倒钩，像在做身份。"),
            new AdvancedTermCard("深水狼", "身份术语", "B", "存在感低、避免冲突的狼人。", "残局存活与收割", "9号像深水狼，全程不接核心矛盾。"),
            new AdvancedTermCard("穿衣服", "发言术语", "B", "用特定口吻或逻辑伪装某身份。", "狼人伪装神职", "他这轮发言在穿预言家衣服。"),
            new AdvancedTermCard("抿身份", "博弈术语", "B", "通过话术和行为细节判断他人身份。", "夜前决策或归票", "我抿2号像神职，先别出。"),
            new AdvancedTermCard("爆点", "逻辑术语", "B", "发言中暴露身份或阵营倾向的关键漏洞。", "快速聚焦怀疑对象", "他说错夜间信息是爆点。"),
            new AdvancedTermCard("金水", "信息术语", "A", "被预言家验明为好人的玩家。", "建立可信好人链", "5号金水先放到后置位考虑。"),
            new AdvancedTermCard("查杀", "信息术语", "A", "被预言家验明为狼人的玩家。", "白天放逐优先级判断", "7号查杀，今天先处理。"),
            new AdvancedTermCard("对跳", "局势术语", "A", "同一神职位出现多名玩家宣称身份。", "形成主线博弈", "现在预言家对跳，先听警下票型。"),
            new AdvancedTermCard("轮次价值", "策略术语", "A", "某行动在当前轮次带来的信息收益。", "平衡生存与出人", "今天出4号轮次价值更高。"),
            new AdvancedTermCard("票型结构", "概率术语", "A", "投票分布体现的阵营关系和站边网络。", "复盘找同伴关系", "这轮票型结构明显分成两团。"),
            new AdvancedTermCard("脏身份", "残局术语", "S", "通过承担可疑动作换取团队收益。", "狼队高阶配合", "我这轮主动脏身份保深水位。"),
            new AdvancedTermCard("垫飞", "残局术语", "S", "用低价值狼牌吸收火力保护核心狼。", "狼人团队牺牲策略", "这票像在垫飞冲锋狼。"),
            new AdvancedTermCard("拉爆", "残局术语", "S", "利用激烈对抗把场面拉入混乱状态。", "信息不对称时反制", "他连续拉爆是想抹平逻辑线。"),
            new AdvancedTermCard("反心态", "心理术语", "S", "利用他人情绪预期反向塑造可信度。", "高压对局心理战", "我反心态发言，先认一个弱点再打逻辑。"),
            new AdvancedTermCard("镜像发言", "高阶话术", "S", "复述对手结构再替换关键结论。", "压制对手节奏", "我镜像你的逻辑，但结论恰好相反。")
    );

    private static final List<GestureCard> GESTURE_CARDS = List.of(
            new GestureCard("数字手势 1-12", "号码沟通", "单手依次比数字，避免遮挡胸口区域。", "快速定位玩家座位号。", "夜间法官提示、白天无声指认", "动作幅度小，避免误触发其他战术手势"),
            new GestureCard("过麦", "流程手势", "手掌向外轻推一次。", "表示本轮发言结束。", "白天发言轮转", "不要连续重复，避免被误解为催促"),
            new GestureCard("归票", "战术手势", "食指指向目标后握拳停顿1秒。", "建议全场投票目标。", "白天放逐前最后总结", "确认法官允许手势沟通后再使用"),
            new GestureCard("先听后打", "战术手势", "手掌下压两次再指向耳朵。", "先收集信息，暂不强打。", "警上冲突过强时降温", "搭配平稳表情，避免被看成心虚"),
            new GestureCard("身份存疑", "身份手势", "食指轻点太阳穴一次。", "该玩家逻辑存在关键疑点。", "提醒队友关注漏洞点", "只提示一次，避免暴露过多思路"),
            new GestureCard("需要保护", "身份手势", "双手短暂交叉后分开。", "提示神职保护关键位。", "夜前讨论或遗言提醒", "不要指向具体神职，防止穿神"),
            new GestureCard("暂停情绪", "情绪管理", "掌心向下缓慢下压。", "提醒己方降低语气与强度。", "争吵升级时稳定场面", "动作慢且小，避免被判定挑衅"),
            new GestureCard("可验可推", "策略手势", "两指先点自己再点目标号。", "该位可做优先处理对象。", "警下定序", "仅在团队有共识时使用")
    );

    public AdvancedTermsResponse getAdvancedTerms(String rankTier, String searchKeyword) {
        int unlockLevel = mapTierToLevel(rankTier);
        String normalizedKeyword = normalize(searchKeyword);
        List<AdvancedTermCard> cards = new ArrayList<>();
        for (AdvancedTermCard card : TERM_LIBRARY) {
            if (levelOf(card.level()) > unlockLevel) {
                continue;
            }
            if (StringUtils.hasText(normalizedKeyword)) {
                String haystack = normalize(card.term() + " " + card.category() + " " + card.definition() + " " + card.usageScenario());
                if (!haystack.contains(normalizedKeyword)) {
                    continue;
                }
            }
            cards.add(card);
        }
        return new AdvancedTermsResponse(
                resolveTierLabel(rankTier),
                unlockLevel,
                cards.size(),
                cards
        );
    }

    public GestureTeachingResponse getGestureTeaching() {
        return new GestureTeachingResponse("线下面杀规范手势库", GESTURE_CARDS);
    }

    public GrowthPlanResponse buildGrowthPlan(WerewolfAnalysisRequest request) {
        String playerLabel = resolvePlayerLabel(request);
        int speechCount = request == null || request.speeches() == null ? 0 : request.speeches().size();
        int voteCount = request == null || request.voteRecords() == null ? 0 : request.voteRecords().size();
        int signalCount = request == null || request.observedSignals() == null ? 0 : request.observedSignals().size();
        int lastWordCount = request == null || request.lastWordRecords() == null ? 0 : request.lastWordRecords().size();

        String stage;
        if (speechCount <= 4) {
            stage = "新手起步：建议先固定一套“结论-证据-归票”三段式发言框架。";
        } else if (signalCount <= 1 || voteCount <= 1) {
            stage = "进阶过渡：你有发言基础，但信息结构化记录不足，影响复盘精度。";
        } else {
            stage = "中高阶提升：可开始做身份博弈与票型联动训练。";
        }

        List<String> weaknessTags = new ArrayList<>();
        if (speechCount <= 4) {
            weaknessTags.add("发言轮次样本少");
        }
        if (voteCount <= 1) {
            weaknessTags.add("投票复盘数据不足");
        }
        if (signalCount <= 1) {
            weaknessTags.add("微表情/语态观察不足");
        }
        if (lastWordCount == 0) {
            weaknessTags.add("遗言利用率偏低");
        }
        if (weaknessTags.isEmpty()) {
            weaknessTags.add("可重点突破：中后期归票强度与情绪稳定度");
        }

        List<String> dailyMissions = List.of(
                "任务1（10分钟）：选1局历史对话，提炼3条“可验证信息点”，禁止用情绪词。",
                "任务2（8分钟）：完成1次表水演练，结构固定为“身份视角-票型解释-明日验证标准”。",
                "任务3（6分钟）：做1次身份速抿训练，给3名玩家写下狼面概率并标注依据。",
                "任务4（5分钟）：情绪节奏训练，发言语速控制在稳定区间并减少口头禅。"
        );

        List<String> emergencyTemplates = List.of(
                "被查杀应急：我先不聊身份情绪，先聊逻辑。你给我的查验与前置发言矛盾，我请你按轮次重述。",
                "被质疑逻辑应急：我只回应一个核心点，我的票型与结论是同向的，不存在先后打架。",
                "被强推应急：今天若一定出我，请留验人顺序与归票标准，明天按结果反证我这轮观点。"
        );

        List<String> transferableSkills = List.of(
                "职场会议：先结论后证据的表达结构，减少沟通成本。",
                "社交识人：通过前后叙述一致性识别话术漏洞。",
                "冲突沟通：在高压场景保持语速稳定与情绪可控。",
                "谈判协作：用可验证事实替代主观判断，提升说服力。"
        );

        return new GrowthPlanResponse(
                playerLabel,
                stage,
                weaknessTags,
                dailyMissions,
                emergencyTemplates,
                transferableSkills
        );
    }

    private String resolvePlayerLabel(WerewolfAnalysisRequest request) {
        if (request == null || request.myPlayerId() == null || request.myPlayerId() <= 0) {
            return "匿名玩家";
        }
        String role = request.myRoleHint();
        if (!StringUtils.hasText(role)) {
            role = "未知身份";
        }
        return request.myPlayerId() + "号位 · " + role;
    }

    private int mapTierToLevel(String rankTier) {
        String tier = resolveTierLabel(rankTier);
        return switch (tier) {
            case "S" -> 4;
            case "A" -> 3;
            case "B" -> 2;
            default -> 1;
        };
    }

    private String resolveTierLabel(String rankTier) {
        if (!StringUtils.hasText(rankTier)) {
            return "C";
        }
        String normalized = rankTier.trim().toUpperCase(Locale.ROOT);
        if ("S".equals(normalized) || "A".equals(normalized) || "B".equals(normalized) || "C".equals(normalized)) {
            return normalized;
        }
        return "C";
    }

    private int levelOf(String level) {
        return switch (resolveTierLabel(level)) {
            case "S" -> 4;
            case "A" -> 3;
            case "B" -> 2;
            default -> 1;
        };
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }
}
