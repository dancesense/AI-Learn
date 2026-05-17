package cn.hollis.llm.mentor.werewolf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 语音识别服务 —— 调用 DashScope qwen3-asr-flash 模型（OpenAI 兼容模式）
 * 接收 base64 音频数据，同步返回转录文本。
 */
@Service
public class WerewolfAsrService {

    private static final Logger log = LoggerFactory.getLogger(WerewolfAsrService.class);
    private static final String ASR_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /**
     * 将 base64 编码的音频数据发送到 DashScope qwen3-asr-flash 进行语音识别。
     *
     * @param base64Audio Data URL 格式，如 "data:audio/webm;base64,UklGRi..."
     * @return 识别出的文本，失败时返回空字符串
     */
    public String transcribe(String base64Audio) {
        try {
            Map<String, Object> audioContent = Map.of(
                    "type", "input_audio",
                    "input_audio", Map.of("data", base64Audio)
            );
            Map<String, Object> userMessage = Map.of(
                    "role", "user",
                    "content", List.of(audioContent)
            );
            Map<String, Object> body = Map.of(
                    "model", "qwen3-asr-flash",
                    "messages", List.of(userMessage),
                    "stream", false,
                    "asr_options", Map.of(
                            "language", "zh",
                            "enable_itn", true
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.debug("[ASR] Sending audio to DashScope, audioBase64 length={}", base64Audio.length());

            ResponseEntity<String> response = restTemplate.exchange(ASR_URL, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String text = choices.get(0).path("message").path("content").asText("");
                    log.info("[ASR] Transcription result: length={}, preview={}", text.length(),
                            text.length() <= 80 ? text : text.substring(0, 80) + "...");
                    return text;
                }
            }
            log.warn("[ASR] Unexpected response: status={}", response.getStatusCode());
            return "";
        } catch (Exception e) {
            log.error("[ASR] Call failed", e);
            return "";
        }
    }
}
