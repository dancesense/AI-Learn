package cn.hollis.llm.mentor.werewolf.controller;

import cn.hollis.llm.mentor.werewolf.service.WerewolfAsrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 语音识别 REST 接口
 * 接收前端 base64 音频数据，调用 DashScope qwen3-asr-flash 返回转录文本。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/werewolf/live")
public class WerewolfAsrController {

    private static final Logger log = LoggerFactory.getLogger(WerewolfAsrController.class);

    private final WerewolfAsrService asrService;

    public WerewolfAsrController(WerewolfAsrService asrService) {
        this.asrService = asrService;
    }

    @PostMapping("/asr")
    public ResponseEntity<?> transcribe(@RequestBody Map<String, String> request) {
        String audio = request.get("audio");
        if (audio == null || audio.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "audio field is required"));
        }

        log.info("[ASR] Received audio transcription request, base64 length={}", audio.length());

        String text = asrService.transcribe(audio);
        return ResponseEntity.ok(Map.of("text", text));
    }
}
