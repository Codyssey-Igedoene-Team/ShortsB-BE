package com.shortsb.embedding.dto;

// 사전 생성 manifest에서 가져올 벡터 호환 기준
public record EmbeddingModel(String modelId, String revision, String fingerprint, int dimension, boolean normalized) {

    public EmbeddingModel {
        if (modelId == null || modelId.isBlank() || revision == null || !revision.matches("[0-9a-f]{40}")
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                || dimension < 1 || !normalized) {
            throw new IllegalArgumentException("정규화된 사전 임베딩의 모델 정보가 필요합니다.");
        }
    }

    // 모델 버전·설정·차원·정규화 일치 확인
    public boolean matches(String modelId, String revision, String fingerprint, Integer dimension, Boolean normalized) {
        return this.modelId.equals(modelId) && this.revision.equals(revision)
                && this.fingerprint.equals(fingerprint) && Integer.valueOf(this.dimension).equals(dimension)
                && Boolean.valueOf(this.normalized).equals(normalized);
    }
}
