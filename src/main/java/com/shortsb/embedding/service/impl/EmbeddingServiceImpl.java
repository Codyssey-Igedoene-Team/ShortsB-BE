package com.shortsb.embedding.service.impl;

import com.shortsb.embedding.dto.EmbeddingHealthResponse;
import com.shortsb.embedding.dto.EmbeddingModel;
import com.shortsb.embedding.dto.EmbeddingRequest;
import com.shortsb.embedding.dto.EmbeddingResponse;
import com.shortsb.embedding.exception.EmbeddingException;
import com.shortsb.embedding.service.EmbeddingService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
// FastAPI 호출과 사전 벡터 호환성 검증 구현
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RestClient client;

    public EmbeddingServiceImpl(@Qualifier("embeddingRestClient") RestClient client) {
        this.client = client;
    }

    // warm-up·실제 GPU·기대 모델 준비 확인
    @Override
    public EmbeddingHealthResponse checkHealth(EmbeddingModel expectedModel) {
        Objects.requireNonNull(expectedModel, "사전 벡터의 모델 정보가 필요합니다.");
        EmbeddingHealthResponse response = execute(client.get().uri("/health").accept(MediaType.APPLICATION_JSON),
                EmbeddingHealthResponse.class);
        if (response == null) {
            throw invalidResponse();
        }
        if (!Boolean.TRUE.equals(response.ready())) {
            throw unavailable();
        }
        requireGpu(response.device(), response.gpuReady());
        requireCompatible(expectedModel, response.modelId(), response.revision(), response.fingerprint(),
                response.dimension(), response.normalized());
        return response;
    }

    // 프롬프트 단건 요청과 ID·벡터 검증
    @Override
    public List<Double> embedQuery(String text, EmbeddingModel expectedModel) {
        Objects.requireNonNull(expectedModel, "사전 벡터의 모델 정보가 필요합니다.");
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 32768
                || !StandardCharsets.UTF_8.newEncoder().canEncode(text)) {
            throw new EmbeddingException(HttpStatus.BAD_REQUEST, "INVALID_EMBEDDING_INPUT",
                    "비어 있지 않은 유효한 프롬프트가 필요합니다.");
        }
        EmbeddingRequest request = EmbeddingRequest.query(text);
        EmbeddingResponse response = execute(client.post().uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(request),
                EmbeddingResponse.class);
        if (response == null || !"query".equals(response.role()) || response.items() == null
                || response.items().size() != 1 || response.items().getFirst() == null
                || !request.items().getFirst().id().equals(response.items().getFirst().id())) {
            throw invalidResponse();
        }
        requireGpu(response.device(), response.gpuReady());
        requireCompatible(expectedModel, response.modelId(), response.revision(), response.fingerprint(),
                response.dimension(), response.normalized());
        List<Double> vector = response.items().getFirst().embedding();
        if (vector == null || vector.size() != expectedModel.dimension()) {
            throw invalidResponse();
        }
        double sum = 0;
        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw invalidResponse();
            }
            sum += value * value;
        }
        if (Math.abs(Math.sqrt(sum) - 1.0) > 1e-3) {
            throw invalidResponse();
        }
        return List.copyOf(vector);
    }

    // 재시도 없는 단일 호출과 외부 오류 본문 차단
    private <T> T execute(RestClient.RequestHeadersSpec<?> request, Class<T> responseType) {
        try {
            return request.retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (httpRequest, response) -> {
                        if (response.getStatusCode().value() == 422) {
                            throw new EmbeddingException(HttpStatus.BAD_REQUEST, "EMBEDDING_INPUT_REJECTED",
                                    "프롬프트 형식과 모델 입력 길이를 확인해주세요.");
                        }
                        if (response.getStatusCode().value() == 429) {
                            throw new EmbeddingException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_BUSY",
                                    "임베딩 서버가 처리 중입니다. 잠시 후 다시 시도해주세요.");
                        }
                        throw unavailable();
                    })
                    .body(responseType);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    private static void requireGpu(String device, Boolean gpuReady) {
        if (!Boolean.TRUE.equals(gpuReady) || device == null || !device.matches("cuda(?::[0-9]+)?")) {
            throw new EmbeddingException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_GPU_REQUIRED",
                    "임베딩 GPU 준비 상태를 확인해주세요.");
        }
    }

    private static void requireCompatible(EmbeddingModel expected, String modelId, String revision,
            String fingerprint, Integer dimension, Boolean normalized) {
        if (!expected.matches(modelId, revision, fingerprint, dimension, normalized)) {
            throw new EmbeddingException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_MODEL_MISMATCH",
                    "사전 데이터와 임베딩 서버의 모델 설정이 다릅니다.");
        }
    }

    private static EmbeddingException unavailable() {
        return new EmbeddingException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_UNAVAILABLE",
                "임베딩 서버에 연결할 수 없습니다.");
    }

    private static EmbeddingException invalidResponse() {
        return new EmbeddingException(HttpStatus.BAD_GATEWAY, "EMBEDDING_INVALID_RESPONSE",
                "임베딩 서버 응답을 확인할 수 없습니다.");
    }
}
