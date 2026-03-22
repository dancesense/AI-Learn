package cn.hollis.llm.mentor.werewolf.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DashScopeKeyStartupChecker {

    private static final Logger log = LoggerFactory.getLogger(DashScopeKeyStartupChecker.class);

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @PostConstruct
    public void checkAtStartup() {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("DashScope API Key 未读取到。请设置环境变量 DASHSCOPE_API_KEY。");
            return;
        }

        boolean looksValid = apiKey.startsWith("sk-") && apiKey.length() >= 20;
        log.info("DashScope API Key 已加载，格式校验结果: {}，脱敏值: {}", looksValid, mask(apiKey));
    }

    private String mask(String key) {
        if (!StringUtils.hasText(key)) {
            return "(empty)";
        }
        if (key.length() <= 8) {
            return "****";
        }
        String prefix = key.substring(0, 4);
        String suffix = key.substring(key.length() - 4);
        return prefix + "****" + suffix;
    }
}
