package com.shortsb.embedding.service.impl;

import com.shortsb.common.exception.ApiExceptionHandler;
import com.shortsb.embedding.config.EmbeddingProperties;
import com.shortsb.embedding.dto.EmbeddingModel;
import com.shortsb.embedding.dto.EmbeddingRequest;
import com.shortsb.embedding.exception.EmbeddingException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// 외부 서버·DB 없이 임베딩 HTTP 계약 검사. 사용자 실행용
class EmbeddingServiceImplTests {

    private static final String BASE_URL = "http://embedding.test";
    private static final String TEXT = "  다시 시작하는 합성 이야기 😀  ";
    private static final EmbeddingModel MODEL = new EmbeddingModel(
            "test-model", "a".repeat(40), "b".repeat(64), 2, true);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private EmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL)
                .defaultHeader("X-API-Key", "test-only-key");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new EmbeddingServiceImpl(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    // 원문 유지·query 단건·ID 연결과 유효 벡터 반환
    @Test
    void queryPreservesTextAndValidatesResponse() {
        stubQuery(response -> response.put("maxTokens", 8192));
        assertThat(service.embedQuery(TEXT, MODEL)).containsExactly(1.0, 0.0);
    }

    // 다른 ID·틀린 차원·정규화 실패 거절
    @Test
    void invalidItemsAndVectorsAreRejected() {
        stubQuery(response -> response.put("items", List.of(Map.of("id", "wrong", "embedding", List.of(1.0, 0.0)))));
        stubQuery(response -> response.put("items", List.of()));
        stubQuery(response -> response.put("items", List.of(Map.of("id", itemId(response), "embedding", List.of(1.0)))));
        stubQuery(response -> response.put("items", List.of(Map.of("id", itemId(response), "embedding", List.of(0.0, 0.0)))));
        stubQuery(response -> response.put("items", List.of(Map.of("id", itemId(response), "embedding", List.of(1e308, 0.0)))));
        for (int i = 0; i < 5; i++) {
            assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);
        }
    }

    // 사전 manifest와 다른 설정·CPU 추론 거절
    @Test
    void incompatibleModelAndCpuInferenceAreRejected() {
        stubQuery(response -> response.put("fingerprint", "c".repeat(64)));
        stubQuery(response -> response.put("normalized", false));
        stubQuery(response -> response.put("gpuReady", false));
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_MODEL_MISMATCH", HttpStatus.SERVICE_UNAVAILABLE);
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_MODEL_MISMATCH", HttpStatus.SERVICE_UNAVAILABLE);
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_GPU_REQUIRED", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 준비 완료와 GPU·모델 상태 확인
    @Test
    void healthRequiresReadyGpuAndCompatibleModel() {
        Map<String, Object> body = response("unused");
        body.put("ready", true);
        server.expect(requestTo(BASE_URL + "/health")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(body), MediaType.APPLICATION_JSON));
        Map<String, Object> cpu = new LinkedHashMap<>(body);
        cpu.put("device", "cpu");
        cpu.put("gpuReady", false);
        server.expect(requestTo(BASE_URL + "/health"))
                .andRespond(withSuccess(mapper.writeValueAsString(cpu), MediaType.APPLICATION_JSON));
        assertThat(service.checkHealth(MODEL).ready()).isTrue();
        assertError(() -> service.checkHealth(MODEL), "EMBEDDING_GPU_REQUIRED", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 외부 오류 본문·연결 예외의 비밀값 노출과 자동 재시도 방지
    @Test
    void remoteErrorsAreSanitizedWithoutRetry() {
        for (HttpStatus status : List.of(HttpStatus.valueOf(422), HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.UNAUTHORIZED)) {
            server.expect(requestTo(BASE_URL + "/embeddings"))
                    .andRespond(withStatus(status).body("숨겨야 하는 외부 응답·인증값"));
        }
        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andRespond(withSuccess("JSON이 아닌 응답", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andRespond(withException(new IOException("숨겨야 하는 연결 정보")));
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_INPUT_REJECTED", HttpStatus.BAD_REQUEST);
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_BUSY", HttpStatus.SERVICE_UNAVAILABLE);
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);
        assertError(() -> service.embedQuery(TEXT, MODEL), "EMBEDDING_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // 잘못된 입력의 HTTP 호출 전 거절
    @Test
    void invalidTextNeverCallsServer() {
        for (String text : List.of(" ", "x".repeat(32769), "\uD800")) {
            assertError(() -> service.embedQuery(text, MODEL), "INVALID_EMBEDDING_INPUT", HttpStatus.BAD_REQUEST);
        }
    }

    // 접속 설정과 안전한 공통 오류 응답 검사
    @Test
    void validatesPropertiesAndMapsSafeErrors() {
        var defaults = new EmbeddingProperties(null, null, null, null);
        assertThat(defaults.baseUrl()).isEqualTo(URI.create("http://127.0.0.1:18001"));
        assertThat(defaults.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        var properties = new EmbeddingProperties(URI.create(BASE_URL), null, Duration.ofSeconds(2), Duration.ofSeconds(30));
        assertThat(properties.apiKey()).isEmpty();
        assertThatThrownBy(() -> new EmbeddingProperties(URI.create(BASE_URL + "/path"), "",
                Duration.ofSeconds(2), Duration.ofSeconds(30))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbeddingProperties(URI.create(BASE_URL), "",
                Duration.ZERO, Duration.ofSeconds(30))).isInstanceOf(IllegalArgumentException.class);
        var response = new ApiExceptionHandler().handleEmbedding(new EmbeddingException(
                HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_BUSY", "잠시 후 다시 시도해주세요."));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("EMBEDDING_BUSY");
    }

    private void stubQuery(Consumer<Map<String, Object>> change) {
        server.expect(requestTo(BASE_URL + "/embeddings"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-API-Key", "test-only-key"))
                .andRespond(request -> {
                    var body = mapper.readValue(((MockClientHttpRequest) request).getBodyAsString(), EmbeddingRequest.class);
                    assertThat(body.role()).isEqualTo("query");
                    assertThat(body.items()).hasSize(1);
                    assertThat(body.items().getFirst().text()).isEqualTo(TEXT);
                    Map<String, Object> result = response(body.items().getFirst().id());
                    change.accept(result);
                    return withSuccess(mapper.writeValueAsString(result), MediaType.APPLICATION_JSON).createResponse(request);
                });
    }

    private static Map<String, Object> response(String id) {
        return new LinkedHashMap<>(Map.of("modelId", MODEL.modelId(), "revision", MODEL.revision(),
                "fingerprint", MODEL.fingerprint(), "dimension", MODEL.dimension(), "normalized", true,
                "device", "cuda:0", "gpuReady", true, "role", "query",
                "items", List.of(Map.of("id", id, "embedding", List.of(1.0, 0.0)))));
    }

    private static String itemId(Map<String, Object> response) {
        return (String) ((Map<?, ?>) ((List<?>) response.get("items")).getFirst()).get("id");
    }

    private static void assertError(Runnable action, String code, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(EmbeddingException.class, error -> {
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.status()).isEqualTo(status);
            assertThat(error.getMessage()).doesNotContain("숨겨야 하는");
            assertThat(error.getCause()).isNull();
        });
    }
}
