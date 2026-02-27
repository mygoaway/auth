package com.jay.auth.service.metrics;

import com.jay.auth.domain.enums.ChannelCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;

/**
 * {@link AuthTimed} 어노테이션이 붙은 메서드의 실행 시간을 Micrometer Timer로 기록하는 AOP Aspect.
 *
 * <p>측정 메트릭: {@code auth_operation_duration_seconds}
 * <p>태그: operation, channel, success
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuthTimedAspect {

    private static final String METRIC_NAME = "auth_operation_duration_seconds";

    private final MeterRegistry registry;

    @Around("@annotation(authTimed)")
    public Object measureDuration(ProceedingJoinPoint joinPoint, AuthTimed authTimed) throws Throwable {
        String operation = authTimed.operation();
        String channel = resolveChannel(joinPoint, authTimed.channelParam());

        Timer.Sample sample = Timer.start(registry);
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            String successTag = String.valueOf(success);
            sample.stop(Timer.builder(METRIC_NAME)
                    .description("인증 서비스 주요 작업 실행 시간")
                    .tag("operation", operation)
                    .tag("channel", channel)
                    .tag("success", successTag)
                    .register(registry));

            log.debug("[AuthTimed] operation={}, channel={}, success={}", operation, channel, successTag);
        }
    }

    /**
     * 메서드 파라미터에서 채널 값을 추출한다.
     *
     * <p>우선순위:
     * <ol>
     *   <li>{@link AuthTimed#channelParam()}에 지정된 이름의 파라미터</li>
     *   <li>{@link ChannelCode} 타입 파라미터 자동 탐지</li>
     *   <li>파라미터 이름이 "channelCode" 또는 "channel"인 String 파라미터</li>
     *   <li>찾지 못하면 "UNKNOWN"</li>
     * </ol>
     */
    private String resolveChannel(ProceedingJoinPoint joinPoint, String channelParam) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        // 1. 이름 지정된 파라미터 우선
        if (!channelParam.isEmpty()) {
            for (int i = 0; i < parameters.length; i++) {
                if (channelParam.equals(parameters[i].getName()) && args[i] != null) {
                    return extractChannelString(args[i]);
                }
            }
        }

        // 2. ChannelCode 타입 파라미터 자동 탐지
        for (int i = 0; i < parameters.length; i++) {
            if (ChannelCode.class.equals(parameters[i].getType()) && args[i] != null) {
                return ((ChannelCode) args[i]).name();
            }
        }

        // 3. 이름 기반 String 파라미터 탐지
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            if (("channelCode".equals(paramName) || "channel".equals(paramName))
                    && args[i] instanceof String s) {
                return s;
            }
        }

        return "UNKNOWN";
    }

    private String extractChannelString(Object value) {
        if (value instanceof ChannelCode cc) {
            return cc.name();
        }
        return value.toString();
    }
}
