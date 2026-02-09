package com.jay.auth.service;

import com.jay.auth.domain.entity.AuditLog;
import com.jay.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional
    public void log(Long userId, String action, String target, String detail, boolean success) {
        String ipAddress = null;
        String userAgent = null;

        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = getClientIp(request);
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // RequestContext not available (e.g., async without propagation)
        }

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .target(target)
                .detail(detail)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .isSuccess(success)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log: userId={}, action={}, target={}, success={}", userId, action, target, success);
    }

    public void log(Long userId, String action, String target) {
        log(userId, action, target, null, true);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
