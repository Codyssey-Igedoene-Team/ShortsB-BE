package com.shortsb.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.recommendation")
// 카드와 자유 프롬프트를 함께 사용할 때의 점수 비중
public record RecommendationProperties(Double promptWeight) {

    public RecommendationProperties {
        promptWeight = promptWeight == null ? 0.6 : promptWeight;
        if (!Double.isFinite(promptWeight) || promptWeight < 0 || promptWeight > 1) {
            throw new IllegalArgumentException("프롬프트 가중치는 0부터 1 사이여야 합니다.");
        }
    }
}
