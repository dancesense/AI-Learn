package cn.hollis.llm.mentor.werewolf.controller;

import cn.hollis.llm.mentor.werewolf.model.MonteCarloInferenceResponse;
import cn.hollis.llm.mentor.werewolf.model.PostGameReviewResponse;
import cn.hollis.llm.mentor.werewolf.model.PsychologyCoachResponse;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.RoleAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.SpeechAdviceResponse;
import cn.hollis.llm.mentor.werewolf.model.WinRateAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.service.MonteCarloAssignmentService;
import cn.hollis.llm.mentor.werewolf.service.WerewolfAnalysisService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/werewolf")
public class WerewolfAnalysisController {

    private final WerewolfAnalysisService werewolfAnalysisService;
    private final MonteCarloAssignmentService monteCarloAssignmentService;

    public WerewolfAnalysisController(WerewolfAnalysisService werewolfAnalysisService,
                                      MonteCarloAssignmentService monteCarloAssignmentService) {
        this.werewolfAnalysisService = werewolfAnalysisService;
        this.monteCarloAssignmentService = monteCarloAssignmentService;
    }

    @PostMapping("/analyze")
    public WerewolfAnalysisResponse analyze(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyze(request);
    }

    @PostMapping("/speech-advice")
    public SpeechAdviceResponse speechAdvice(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyzeSpeechAdvice(request);
    }

    @PostMapping("/role-probabilities")
    public RoleAnalysisResponse roleProbabilities(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyzePlayerRoles(request);
    }

    @PostMapping("/win-rates")
    public WinRateAnalysisResponse winRates(@RequestBody WerewolfAnalysisRequest request) {
        return werewolfAnalysisService.analyzeWinRates(request);
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
}
