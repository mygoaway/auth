package com.jay.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "log", matchIfMissing = true)
public class LogAiReplyService implements AiReplyService {

    @Override
    public String generateReply(String title, String content, String category) {
        log.info("[AI Reply Stub] Generating reply for post: title='{}', category='{}'", title, category);
        return "안녕하세요, 고객센터입니다.\n\n"
                + "문의하신 내용을 확인하였습니다. "
                + "담당자가 확인 후 빠른 시일 내에 답변 드리겠습니다.\n\n"
                + "추가 문의사항이 있으시면 언제든 말씀해주세요.\n\n"
                + "(이 메시지는 AI 자동응답입니다)";
    }
}
