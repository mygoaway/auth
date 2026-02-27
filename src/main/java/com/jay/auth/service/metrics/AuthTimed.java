package com.jay.auth.service.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증 서비스 메서드 실행 시간을 Micrometer Timer로 측정하는 어노테이션.
 *
 * <p>메트릭 이름: {@code auth_operation_duration_seconds}
 * <p>태그:
 * <ul>
 *   <li>{@code operation} — 작업 이름 (예: "email_login", "oauth2_login")</li>
 *   <li>{@code channel}   — 채널/IDP 이름. 런타임에 주입 불가한 경우 "unknown"으로 기록</li>
 *   <li>{@code success}   — 성공 여부 ("true" / "false")</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthTimed {

    /**
     * 작업 이름 (operation 태그 값).
     * 예: "email_login", "oauth2_login", "password_change", "totp_verify", "passkey_register" 등
     */
    String operation();

    /**
     * 채널 태그를 파라미터에서 추출할 때 사용할 파라미터 이름.
     * 빈 문자열이면 channel 태그를 "UNKNOWN"으로 고정한다.
     *
     * <p>AOP에서 메서드 파라미터 이름을 리플렉션으로 읽어 ChannelCode 또는 String 타입 파라미터를 찾는다.
     */
    String channelParam() default "";
}
