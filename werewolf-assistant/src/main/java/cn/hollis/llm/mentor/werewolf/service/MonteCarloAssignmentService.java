package cn.hollis.llm.mentor.werewolf.service;

import cn.hollis.llm.mentor.werewolf.model.McPlayerMarginal;
import cn.hollis.llm.mentor.werewolf.model.MonteCarloInferenceResponse;
import cn.hollis.llm.mentor.werewolf.model.RoleProbability;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 在板子角色 multiset 与已知硬约束下，对剩余身份做均匀随机排列采样，得到边缘概率（模块一：决策大脑 / 蒙特卡洛基线）。
 */
@Service
public class MonteCarloAssignmentService {

    private static final int DEFAULT_SAMPLES = 4000;
    private static final int MAX_SAMPLES = 50_000;

    public MonteCarloInferenceResponse infer(WerewolfAnalysisRequest request) {
        return infer(request, DEFAULT_SAMPLES);
    }

    public MonteCarloInferenceResponse infer(WerewolfAnalysisRequest request, int samples) {
        if (request == null || request.totalPlayers() == null || request.totalPlayers() <= 0) {
            return new MonteCarloInferenceResponse("", "", 0, List.of(), "总人数无效，无法模拟");
        }
        int n = request.totalPlayers();
        if (samples < 100) {
            samples = 100;
        }
        if (samples > MAX_SAMPLES) {
            samples = MAX_SAMPLES;
        }
        List<String> multiset = expandRoleMultiset(request.roleComposition());
        if (multiset.size() != n) {
            return new MonteCarloInferenceResponse(
                    resolveMode(request),
                    resolvePhase(request),
                    0,
                    List.of(),
                    "角色构成数量与总人数不一致：期望 " + n + " 个身份，实际展开为 " + multiset.size() + " 个"
            );
        }

        LinkedHashMap<Integer, String> fixed = buildFixedAssignments(request);
        String conflict = validateFixedAgainstMultiset(multiset, fixed);
        if (conflict != null) {
            return new MonteCarloInferenceResponse(resolveMode(request), resolvePhase(request), 0, List.of(), conflict);
        }

        List<String> remainingRoles = new ArrayList<>(multiset);
        for (String role : fixed.values()) {
            if (!remainingRoles.remove(role)) {
                return new MonteCarloInferenceResponse(resolveMode(request), resolvePhase(request), 0, List.of(),
                        "固定身份与板子 multiset 不一致：" + role);
            }
        }

        List<Integer> openPlayers = new ArrayList<>();
        for (int pid = 1; pid <= n; pid++) {
            if (!fixed.containsKey(pid)) {
                openPlayers.add(pid);
            }
        }
        if (openPlayers.size() != remainingRoles.size()) {
            return new MonteCarloInferenceResponse(resolveMode(request), resolvePhase(request), 0, List.of(),
                    "内部错误：开放人数与剩余身份数不匹配");
        }

        Map<Integer, Map<String, Long>> counts = new HashMap<>();
        for (int pid = 1; pid <= n; pid++) {
            counts.put(pid, new HashMap<>());
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> deck = new ArrayList<>(remainingRoles);
        for (int s = 0; s < samples; s++) {
            Collections.shuffle(deck, rnd);
            Map<Integer, String> assignment = new HashMap<>(fixed);
            for (int i = 0; i < openPlayers.size(); i++) {
                assignment.put(openPlayers.get(i), deck.get(i));
            }
            for (int pid = 1; pid <= n; pid++) {
                String role = assignment.get(pid);
                if (role == null) {
                    continue;
                }
                counts.get(pid).merge(role, 1L, Long::sum);
            }
        }

        List<McPlayerMarginal> marginals = new ArrayList<>();
        for (int pid = 1; pid <= n; pid++) {
            Map<String, Long> roleCounts = counts.get(pid);
            List<RoleProbability> probs = new ArrayList<>();
            long total = roleCounts.values().stream().mapToLong(Long::longValue).sum();
            double wolfP = 0;
            if (total > 0) {
                List<Map.Entry<String, Long>> entries = new ArrayList<>(roleCounts.entrySet());
                entries.sort(Comparator.comparing((Map.Entry<String, Long> e) -> -e.getValue()));
                for (Map.Entry<String, Long> e : entries) {
                    double p = e.getValue() / (double) total;
                    probs.add(new RoleProbability(e.getKey(), p));
                    if ("狼人".equals(e.getKey())) {
                        wolfP = p;
                    }
                }
            }
            marginals.add(new McPlayerMarginal(pid, probs, wolfP));
        }

        return new MonteCarloInferenceResponse(
                resolveMode(request),
                resolvePhase(request),
                samples,
                marginals,
                "基于板子 multiset 与已知身份的均匀随机排列采样（未加入发言/投票似然加权，可与 LLM 概率对照使用）"
        );
    }

    private static LinkedHashMap<Integer, String> buildFixedAssignments(WerewolfAnalysisRequest request) {
        LinkedHashMap<Integer, String> fixed = new LinkedHashMap<>();
        if (request.revealedIdentities() != null) {
            for (Map.Entry<Integer, String> e : request.revealedIdentities().entrySet()) {
                if (e.getKey() != null && e.getKey() > 0 && StringUtils.hasText(e.getValue())) {
                    fixed.put(e.getKey(), e.getValue().trim());
                }
            }
        }
        if (request.knownWerewolfPlayers() != null) {
            for (Integer pid : request.knownWerewolfPlayers()) {
                if (pid != null && pid > 0) {
                    mergeFixed(fixed, pid, "狼人");
                }
            }
        }
        if (request.myPlayerId() != null && request.myPlayerId() > 0 && StringUtils.hasText(request.myRoleHint())) {
            String role = request.myRoleHint().trim();
            if (!"未知".equals(role) && !"unknown".equalsIgnoreCase(role) && !"?".equals(role)) {
                mergeFixed(fixed, request.myPlayerId(), role);
            }
        }
        return fixed;
    }

    private static void mergeFixed(Map<Integer, String> fixed, int playerId, String role) {
        if (fixed.containsKey(playerId)) {
            String existing = fixed.get(playerId);
            if (!existing.equals(role)) {
                fixed.put(playerId, existing + "|冲突:" + role);
            }
        } else {
            fixed.put(playerId, role);
        }
    }

    private static String validateFixedAgainstMultiset(List<String> multiset, Map<Integer, String> fixed) {
        for (Map.Entry<Integer, String> e : fixed.entrySet()) {
            if (e.getValue().contains("冲突")) {
                return "玩家" + e.getKey() + " 存在矛盾的身份约束：" + e.getValue();
            }
        }
        List<String> copy = new ArrayList<>(multiset);
        for (String role : fixed.values()) {
            if (!copy.remove(role)) {
                return "固定身份「" + role + "」超出板子 multiset";
            }
        }
        return null;
    }

    private static List<String> expandRoleMultiset(Map<String, Integer> composition) {
        List<String> out = new ArrayList<>();
        if (composition == null) {
            return out;
        }
        for (Map.Entry<String, Integer> e : composition.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            for (int i = 0; i < e.getValue(); i++) {
                out.add(e.getKey().trim());
            }
        }
        return out;
    }

    private static String resolveMode(WerewolfAnalysisRequest request) {
        String mode = request.gameMode();
        if (!StringUtils.hasText(mode)) {
            mode = (request.totalPlayers() == null ? "未指定人数场" : request.totalPlayers() + "人场");
        }
        return mode;
    }

    private static String resolvePhase(WerewolfAnalysisRequest request) {
        return StringUtils.hasText(request.phase()) ? request.phase() : "白天发言阶段";
    }
}
