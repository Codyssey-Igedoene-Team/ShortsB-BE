package com.shortsb.embedding.dto;

import java.util.List;
import java.util.UUID;

// FastAPI 단건 query 요청
public record EmbeddingRequest(String role, List<Item> items) {

    // 요청마다 고유 ID를 부여하는 query 생성
    public static EmbeddingRequest query(String text) {
        return new EmbeddingRequest("query", List.of(new Item(UUID.randomUUID().toString(), text)));
    }

    public record Item(String id, String text) {
    }
}
