package com.shortsb.embedding.service;

import com.shortsb.embedding.dto.EmbeddingHealthResponse;
import com.shortsb.embedding.dto.EmbeddingModel;
import java.util.List;

// 사전 벡터 설정과 호환되는 임베딩 서버 연결 계약
public interface EmbeddingService {

    EmbeddingHealthResponse checkHealth(EmbeddingModel expectedModel);

    List<Double> embedQuery(String text, EmbeddingModel expectedModel);
}
