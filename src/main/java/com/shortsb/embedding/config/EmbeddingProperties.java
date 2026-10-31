package com.shortsb.embedding.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
// 임베딩 서버 주소·내부 인증·호출 제한 시간
public record EmbeddingProperties(URI baseUrl, String apiKey, Duration connectTimeout, Duration readTimeout) {

    public EmbeddingProperties {
        baseUrl = baseUrl == null || baseUrl.toString().isBlank() ? URI.create("http://127.0.0.1:18001") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        if ((!"http".equals(baseUrl.getScheme()) && !"https".equals(baseUrl.getScheme()))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null
                || (!baseUrl.getRawPath().isEmpty() && !"/".equals(baseUrl.getRawPath()))) {
            throw new IllegalArgumentException("app.embedding.base-url에 경로 없는 HTTP(S) 주소가 필요합니다.");
        }
        if (connectTimeout.toMillis() < 1 || readTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("임베딩 연결·응답 제한 시간은 1ms 이상이어야 합니다.");
        }
        apiKey = apiKey == null ? "" : apiKey;
        if (apiKey.contains("\r") || apiKey.contains("\n")) {
            throw new IllegalArgumentException("내부 API 인증키에 줄바꿈을 넣을 수 없습니다.");
        }
    }
}
