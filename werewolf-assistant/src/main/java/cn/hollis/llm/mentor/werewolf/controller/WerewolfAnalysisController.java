package cn.hollis.llm.mentor.werewolf.controller;

import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisRequest;
import cn.hollis.llm.mentor.werewolf.model.WerewolfAnalysisResponse;
import cn.hollis.llm.mentor.werewolf.service.WerewolfAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/werewolf")
public class WerewolfAnalysisController {

    private final WerewolfAnalysisService werewolfAnalysisService;

    public WerewolfAnalysisController(WerewolfAnalysisService werewolfAnalysisService) {
        this.werewolfAnalysisService = werewolfAnalysisService;
    }

    @PostMapping("/analyze")
    public WerewolfAnalysisResponse analyze(@RequestBody WerewolfAnalysisRequest request) {
        WerewolfAnalysisResponse analyze = werewolfAnalysisService.analyze(request);
        System.out.println(analyze);
        return analyze;
    }
}
