package com.shortsb.embedding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
// 모델 정보와 요청 ID에 연결된 임베딩 응답
public record EmbeddingResponse(String modelId, String revision, String fingerprint, Integer dimension,
        Boolean normalized, String device, Boolean gpuReady, String role, List<Item> items) {

    public record Item(String id, List<Double> embedding) {
    }
}
