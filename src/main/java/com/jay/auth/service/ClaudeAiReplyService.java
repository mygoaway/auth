package com.jay.auth.service;

import com.jay.auth.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "claude")
public class ClaudeAiReplyService implements AiReplyService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generateReply(String title, String content, String category) {
        AppProperties.Ai.Claude claudeConfig = appProperties.getAi().getClaude();

        String systemPrompt = "당신은 'Authly' 서비스의 고객센터 AI 상담원입니다. "
                + "사용자의 문의에 대해 친절하고 전문적으로 초기 안내를 제공합니다. "
                + "답변은 한국어로 작성하며, 다음 규칙을 따릅니다:\n"
                + "1. 인사말로 시작합니다.\n"
                + "2. 문의 내용을 이해했음을 알립니다.\n"
                + "3. 가능한 범위 내에서 도움이 되는 안내를 제공합니다.\n"
                + "4. 추가 도움이 필요하면 담당자가 확인 후 답변할 것임을 안내합니다.\n"
                + "5. 답변은 간결하게 작성합니다 (최대 300자).\n"
                + "6. 마크다운 문법을 사용하지 않습니다.";

        String userMessage = String.format("[카테고리: %s]\n제목: %s\n내용: %s", category, title, content);

        Map<String, Object> requestBody = Map.of(
                "model", claudeConfig.getModel(),
                "max_tokens", claudeConfig.getMaxTokens(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", claudeConfig.getApiKey());
        headers.set("anthropic-version", "2023-06-01");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    CLAUDE_API_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> contentBlocks = (List<Map<String, Object>>) response.getBody().get("content");
                if (contentBlocks != null && !contentBlocks.isEmpty()) {
                    return (String) contentBlocks.get(0).get("text");
                }
            }

            log.warn("Unexpected Claude API response: {}", response.getStatusCode());
            return getFallbackReply();
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage(), e);
            return getFallbackReply();
        }
    }

    private String getFallbackReply() {
        return "안녕하세요, 고객센터입니다.\n\n"
                + "문의하신 내용을 확인하였습니다. "
                + "담당자가 확인 후 빠른 시일 내에 답변 드리겠습니다.\n\n"
                + "추가 문의사항이 있으시면 언제든 말씀해주세요.";
    }
}
