package com.shortsb.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
// 추천 점수 설정 등록
public class RecommendationConfig {}
