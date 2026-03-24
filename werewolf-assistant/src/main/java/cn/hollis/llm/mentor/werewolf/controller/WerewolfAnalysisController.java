package cn.hollis.llm.mentor.werewolf.controller;

import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.RoleAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.model.SpeechAdviceResponse;
import cn.hollis.llm.mentor.werewolf.model.WinRateAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.service.WerewolfAnalysisService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/werewolf")
public class WerewolfAnalysisController {

    private final WerewolfAnalysisService werewolfAnalysisService;

    public WerewolfAnalysisController(WerewolfAnalysisService werewolfAnalysisService) {
        this.werewolfAnalysisService = werewolfAnalysisService;
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
}
