package com.shortsb.book.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 도서·DB·GPU를 사용하지 않는 파일 계약 검사. 사용자 실행용
class CatalogImportReaderTests {

    private static final String FIRST_PAGE = "합성 본문 😀";
    private static final String SECOND_PAGE = "다음 합성 페이지";
    // Python json.dumps(sort_keys=True, separators=(",", ":"))와 같은 외부 계약
    private static final String CANONICAL_CONFIG = "{\"dimension\":2,\"documentPrefix\":\"\",\"encoder\":\"test-encode\","
            + "\"libraries\":{\"numpy\":\"test\",\"sentence-transformers\":\"test\",\"torch\":\"test\",\"transformers\":\"test\"},"
            + "\"maxTokens\":8192,\"modelId\":\"test-model\",\"normalized\":true,\"preprocessingVersion\":\"verbatim-v1\","
            + "\"queryPrefix\":\"\",\"revision\":\"" + "a".repeat(40) + "\"}";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CatalogImportReader reader = new CatalogImportReader();

    @TempDir
    Path directory;

    // 이모지 UTF-16 길이·페이지 순서·재결합·출처의 NULL 보존
    @Test
    void catalogPreservesPagesAndOriginalMetadata() throws IOException {
        fixture();
        var data = reader.read(directory, null, null, null);
        assertThat(data.pageCount()).isEqualTo(2);
        assertThat(data.model()).isNull();
        assertThat(data.books().getFirst().pages().getFirst().content()).isEqualTo(FIRST_PAGE);
        assertThat(data.books().getFirst().pages().get(1).content()).isEqualTo(SECOND_PAGE);
        assertThat(data.books().getFirst().metadata().get("licenseURL").isNull()).isTrue();
        assertThat(data.books().getFirst().pages().getFirst().toString()).doesNotContain(FIRST_PAGE);
    }

    // 원문 변조와 UTF-16 대신 코드 포인트 수를 사용한 위치 거절
    @Test
    void wrongBodyHashAndUtf16RangeAreRejected() throws IOException {
        fixture();
        List<JsonNode> rows = inputRows();
        ((ObjectNode) rows.getFirst()).put("endPosition", FIRST_PAGE.codePointCount(0, FIRST_PAGE.length()));
        writeLines("segments.jsonl", rows);
        assertInvalidCatalog();
        fixture();
        rows = inputRows();
        ((ObjectNode) rows.getFirst()).put("text", "다른 본문");
        writeLines("segments.jsonl", rows);
        assertInvalidCatalog();
    }

    // 실제 쪽수·중복 ID·챕터 끝 제외 범위 검사
    @Test
    void invalidCountsDuplicatesAndChapterRangesAreRejected() throws IOException {
        fixture();
        ObjectNode metadata = object("catalog-metadata.json");
        ObjectNode book = (ObjectNode) metadata.get("books").get(0);
        ((ObjectNode) book.get("chapters").get(0)).put("end", 3);
        write("catalog-metadata.json", metadata);
        assertInvalidCatalog();
        fixture();
        List<JsonNode> rows = inputRows();
        writeLines("segments.jsonl", List.of(rows.getFirst(), rows.getFirst()));
        assertInvalidCatalog();
        fixture();
        ObjectNode manifest = object("manifest.json");
        manifest.put("pageCount", 3);
        write("manifest.json", manifest);
        assertInvalidCatalog();
    }

    // JSONB에서 보존 불가능한 문자열과 중복 JSON 필드 거절
    @Test
    void invalidEncodingAndAmbiguousJsonAreRejected() throws IOException {
        fixture();
        ObjectNode metadata = object("catalog-metadata.json");
        ((ObjectNode) metadata.get("books").get(0)).put("translationNote", "잘못된\0문자열");
        write("catalog-metadata.json", metadata);
        assertInvalidCatalog();
        fixture();
        String json = Files.readString(directory.resolve("manifest.json"));
        Files.writeString(directory.resolve("manifest.json"), json.substring(0, json.length() - 1) + ",\"schemaVersion\":1}");
        assertInvalidCatalog();
    }

    // 본문·카드 모델 일치와 세 종류의 정규화 벡터 등록 입력
    @Test
    void completeGpuBundleAcceptsDocumentsAndThreeCards() throws IOException {
        fixture();
        vectors();
        var data = fullBundle();
        assertThat(data.documents()).hasSize(2);
        assertThat(data.cards()).extracting(card -> card.code()).containsExactly("WARM", "HUMOR", "THOUGHTFUL");
        assertThat(data.model().fingerprint()).isEqualTo(CatalogImportReader.sha256(CANONICAL_CONFIG));
        assertThat(reader.canonicalJson(data.model().configuration())).isEqualTo(CANONICAL_CONFIG);
    }

    // CPU 생성·원본 ID 변경·0벡터·차원 오류와 카드 누락 거절
    @Test
    void invalidGpuVectorsAndMissingCardsAreRejected() throws IOException {
        fixture();
        vectors();
        ObjectNode manifest = object("documents/manifest.json");
        manifest.put("device", "cpu");
        manifest.put("gpuReady", false);
        write("documents/manifest.json", manifest);
        assertInvalidBundle();
        vectors();
        List<JsonNode> rows = vectorRows();
        ((ObjectNode) rows.getFirst()).put("id", "different");
        writeLines("documents/vectors.jsonl", rows);
        assertInvalidBundle();
        for (List<Double> values : List.of(List.of(0.0, 0.0), List.of(1.0), List.of(1e308, 0.0))) {
            vectors();
            rows = vectorRows();
            ((ObjectNode) rows.getFirst()).set("embedding", mapper.valueToTree(values));
            writeLines("documents/vectors.jsonl", rows);
            assertInvalidBundle();
        }
        vectors();
        List<String> cards = Files.readAllLines(directory.resolve("cards-input.jsonl"));
        Files.write(directory.resolve("cards-input.jsonl"), cards.subList(0, 2));
        assertInvalidBundle();
    }

    // 모델 설정의 지문 변조·카드/본문의 서로 다른 revision 거절
    @Test
    void mismatchedModelFingerprintIsRejected() throws IOException {
        fixture();
        vectors();
        ObjectNode manifest = object("cards/manifest.json");
        manifest.put("fingerprint", "b".repeat(64));
        write("cards/manifest.json", manifest);
        assertInvalidBundle();
        vectors();
        manifest = object("cards/manifest.json");
        manifest.put("revision", "b".repeat(40));
        write("cards/manifest.json", manifest);
        assertInvalidBundle();
        assertThatThrownBy(() -> reader.read(directory, directory.resolve("documents"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Python 기본 ensure_ascii의 한국어·이모지·DEL 이스케이프와 키 정렬
    @Test
    void canonicalJsonMatchesPythonAsciiEncoding() {
        JsonNode input = mapper.readTree("{\"z\":\"한국😀\",\"a\":\"\\u007f\"}");
        assertThat(reader.canonicalJson(input)).isEqualTo("{\"a\":\"\\u007f\",\"z\":\"\\ud55c\\uad6d\\ud83d\\ude00\"}");
    }

    private void fixture() throws IOException {
        List<String> pages = List.of(FIRST_PAGE, SECOND_PAGE);
        Map<String, Object> book = new LinkedHashMap<>(Map.of("id", "sample", "title", "합성 작품", "author", "합성 저자",
                "edition", "test-edition", "language", "ko", "sourceURL", "https://example.test/source",
                "pageCount", pages.size(), "textSHA256", CatalogImportReader.sha256(String.join("\n", pages)),
                "pageJoiner", "\n", "chapters", List.of(Map.of("id", "chapter-1", "title", "합성 챕터",
                        "start", 0, "end", 2, "available", true))));
        book.put("licenseURL", null);
        write("catalog-metadata.json", Map.of("version", "test-v1", "bookCount", 1, "books", List.of(book)));
        write("manifest.json", Map.of("schemaVersion", 1, "mappingVersion", "provided-pages-utf16-v1",
                "pageIndexBase", 1, "chapterIndexBase", 0, "positionUnit", "UTF-16", "endPositionExclusive", true,
                "sourceSHA256", "a".repeat(64), "sourceVersion", "test-v1", "bookCount", 1,
                "pageCount", 2));
        ObjectNode manifest = object("manifest.json");
        manifest.put("segmentCount", 2);
        write("manifest.json", manifest);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            String text = pages.get(index);
            String hash = CatalogImportReader.sha256(text);
            String id = "sample:p" + (index + 1) + ":" + hash.substring(0, 16);
            rows.add(Map.of("id", id, "segmentId", id, "bookId", "sample", "pageNo", index + 1,
                    "startPosition", 0, "endPosition", text.length(), "text", text, "contentHash", hash));
        }
        writeLines("segments.jsonl", rows);
    }

    private void vectors() throws IOException {
        List<JsonNode> source = inputRows();
        write("documents/manifest.json", modelManifest("document", 2));
        write("cards/manifest.json", modelManifest("query", 3));
        List<JsonNode> documents = new ArrayList<>();
        for (JsonNode row : source) {
            documents.add(vectorRow(row));
        }
        writeLines("documents/vectors.jsonl", documents);
        List<JsonNode> inputs = new ArrayList<>();
        List<JsonNode> cards = new ArrayList<>();
        for (String code : List.of("WARM", "HUMOR", "THOUGHTFUL")) {
            String text = "합성 검색 문장 " + code;
            JsonNode input = mapper.valueToTree(Map.of("id", "card-v1:" + code, "cardCode", code, "text", text,
                    "contentHash", CatalogImportReader.sha256(text)));
            inputs.add(input);
            ObjectNode row = vectorRow(input);
            row.put("role", "query");
            cards.add(row);
        }
        writeLines("cards-input.jsonl", inputs);
        writeLines("cards/vectors.jsonl", cards);
    }

    private ObjectNode vectorRow(JsonNode input) {
        ObjectNode row = (ObjectNode) input.deepCopy();
        row.remove("text");
        row.put("role", "document");
        ArrayNode values = mapper.createArrayNode();
        values.add(1.0).add(0.0);
        row.set("embedding", values);
        return row;
    }

    private ObjectNode modelManifest(String role, int count) {
        ObjectNode manifest = (ObjectNode) mapper.readTree(CANONICAL_CONFIG);
        manifest.put("fingerprint", CatalogImportReader.sha256(CANONICAL_CONFIG));
        manifest.put("schemaVersion", 1);
        manifest.put("role", role);
        manifest.put("recordCount", count);
        manifest.put("device", "cuda:0");
        manifest.put("gpuReady", true);
        return manifest;
    }

    private List<JsonNode> inputRows() throws IOException {
        return Files.readAllLines(directory.resolve("segments.jsonl")).stream().map(mapper::readTree).toList();
    }

    private List<JsonNode> vectorRows() throws IOException {
        return Files.readAllLines(directory.resolve("documents/vectors.jsonl")).stream().map(mapper::readTree).toList();
    }

    private ObjectNode object(String name) throws IOException {
        return (ObjectNode) mapper.readTree(Files.readString(directory.resolve(name)));
    }

    private void write(String name, Object data) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writeValueAsString(data));
    }

    private void writeLines(String name, List<?> rows) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, rows.stream().map(mapper::writeValueAsString).toList());
    }

    private com.shortsb.book.dto.CatalogImportData fullBundle() {
        return reader.read(directory, directory.resolve("documents"), directory.resolve("cards-input.jsonl"), directory.resolve("cards"));
    }

    private void assertInvalidCatalog() {
        assertThatThrownBy(() -> reader.read(directory, null, null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(FIRST_PAGE).hasMessageNotContaining(SECOND_PAGE);
    }

    private void assertInvalidBundle() {
        assertThatThrownBy(this::fullBundle).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(FIRST_PAGE).hasMessageNotContaining(SECOND_PAGE);
    }
}
