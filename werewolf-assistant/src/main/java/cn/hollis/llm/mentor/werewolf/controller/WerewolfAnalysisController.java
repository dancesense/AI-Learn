package cn.hollis.llm.mentor.werewolf.controller;

import cn.hollis.llm.mentor.werewolf.model.MonteCarloInferenceResponse;
import cn.hollis.llm.mentor.werewolf.model.PostGameReviewResponse;
import cn.hollis.llm.mentor.werewolf.model.PsychologyCoachResponse;
import cn.hollis.llm.mentor.werewolf.model.AdvancedTermsRequest;
import cn.hollis.llm.mentor.werewolf.model.AdvancedTermsResponse;
import cn.hollis.llm.mentor.werewolf.model.GestureTeachingResponse;
import cn.hollis.llm.mentor.werewolf.model.GrowthPlanResponse;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.RoleAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.SpeechAdviceResponse;
import cn.hollis.llm.mentor.werewolf.model.WinRateAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.service.MonteCarloAssignmentService;
import cn.hollis.llm.mentor.werewolf.service.WerewolfAnalysisService;
import cn.hollis.llm.mentor.werewolf.service.WerewolfLearningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/werewolf")
public class WerewolfAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(WerewolfAnalysisController.class);

    private final WerewolfAnalysisService werewolfAnalysisService;
    private final MonteCarloAssignmentService monteCarloAssignmentService;
    private final WerewolfLearningService werewolfLearningService;

    public WerewolfAnalysisController(WerewolfAnalysisService werewolfAnalysisService,
                                      MonteCarloAssignmentService monteCarloAssignmentService,
                                      WerewolfLearningService werewolfLearningService) {
        this.werewolfAnalysisService = werewolfAnalysisService;
        this.monteCarloAssignmentService = monteCarloAssignmentService;
        this.werewolfLearningService = werewolfLearningService;
    }

    @PostMapping("/analyze")
    public WerewolfAnalysisResponse analyze(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyze(request);
    }

    @PostMapping("/speech-advice")
    public SpeechAdviceResponse speechAdvice(@RequestBody WerewolfAnalysisRequest request) {
        long startedAt = System.currentTimeMillis();
        String mergedSpeech = mergeSpeeches(request);
        log.info("[speech-advice] request received: phase={}, speechesCount={}, textLen={}, preview={}",
                request == null ? null : request.phase(),
                request == null || request.speeches() == null ? 0 : request.speeches().size(),
                mergedSpeech.length(),
                preview(mergedSpeech));
        SpeechAdviceResponse response = werewolfAnalysisService.analyzeSpeechAdvice(request);
        log.info("[speech-advice] response finished in {} ms, hasStrategy={}",
                System.currentTimeMillis() - startedAt,
                response != null && response.speechStrategy() != null);
        return response;
    }

    @PostMapping("/role-probabilities")
    public RoleAnalysisResponse roleProbabilities(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyzePlayerRoles(request);
    }

    @PostMapping("/win-rates")
    public WinRateAnalysisResponse winRates(@RequestBody WerewolfAnalysisRequest request) {
        long startedAt = System.currentTimeMillis();
        String mergedSpeech = mergeSpeeches(request);
        log.info("[win-rates] request received: phase={}, speechesCount={}, textLen={}, preview={}",
                request == null ? null : request.phase(),
                request == null || request.speeches() == null ? 0 : request.speeches().size(),
                mergedSpeech.length(),
                preview(mergedSpeech));
        WinRateAnalysisResponse response = werewolfAnalysisService.analyzeWinRates(request);
        log.info("[win-rates] response finished in {} ms, roleCount={}",
                System.currentTimeMillis() - startedAt,
                response == null || response.roleWinRates() == null ? 0 : response.roleWinRates().size());
        return response;
    }

    private static String mergeSpeeches(WerewolfAnalysisRequest request) {
        if (request == null || request.speeches() == null || request.speeches().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<cn.hollis.llm.mentor.werewolf.model.PlayerSpeech> speeches = request.speeches();
        for (cn.hollis.llm.mentor.werewolf.model.PlayerSpeech s : speeches) {
            if (s == null || s.speech() == null || s.speech().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(s.speech().trim());
        }
        return sb.toString();
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        String normalized = text.replace('\n', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    @PostMapping("/monte-carlo")
    public MonteCarloInferenceResponse monteCarlo(@RequestBody WerewolfAnalysisRequest request,
                                                  @RequestParam(defaultValue = "4000") int samples) {
        return monteCarloAssignmentService.infer(request, samples);
    }

    @PostMapping("/psychology-coach")
    public PsychologyCoachResponse psychologyCoach(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyzePsychology(request);
    }

    @PostMapping("/post-game-review")
    public PostGameReviewResponse postGameReview(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyzePostGame(request);
    }

    @PostMapping("/learning/advanced-terms")
    public AdvancedTermsResponse advancedTerms(@RequestBody(required = false) AdvancedTermsRequest request) {
        String rankTier = request == null ? null : request.rankTier();
        String searchKeyword = request == null ? null : request.searchKeyword();
        return werewolfLearningService.getAdvancedTerms(rankTier, searchKeyword);
    }

    @PostMapping("/learning/gestures")
    public GestureTeachingResponse gestures() {
        return werewolfLearningService.getGestureTeaching();
    }

    @PostMapping("/learning/growth-plan")
    public GrowthPlanResponse growthPlan(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfLearningService.buildGrowthPlan(request);
    }
}
