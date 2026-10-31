package com.shortsb.auth.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
// 로그인 이동 주소와 CORS 허용 출처 설정
public record AuthProperties(String loginSuccessUrl, String loginFailureUrl, List<String> allowedOrigins) {

    // 로그인 URL과 경로 없는 CORS 출처 검증
    public AuthProperties {
        validateHttpUrl(loginSuccessUrl, "app.auth.login-success-url");
        validateHttpUrl(loginFailureUrl, "app.auth.login-failure-url");
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.auth.allowed-origins가 필요합니다.");
        }
        allowedOrigins = List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            URI uri = validateHttpUrl(origin, "app.auth.allowed-origins");
            if (!uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("CORS 출처에는 경로·쿼리·fragment를 넣을 수 없습니다.");
            }
        }
    }

    private static URI validateHttpUrl(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + "가 필요합니다.");
        }
        URI uri = URI.create(value);
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(property + "에 유효한 HTTP(S) URL이 필요합니다.");
        }
        return uri;
    }
}
