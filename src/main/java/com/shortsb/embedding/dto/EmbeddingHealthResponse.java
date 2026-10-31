package com.shortsb.embedding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
// 모델 warm-up과 GPU 준비 상태 응답
public record EmbeddingHealthResponse(Boolean ready, String modelId, String revision, String fingerprint,
        Integer dimension, Boolean normalized, String device, Boolean gpuReady) {
}
