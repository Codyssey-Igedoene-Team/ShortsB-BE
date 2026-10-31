package com.shortsb.book.service;

import com.shortsb.book.dto.CatalogImportData;
import com.shortsb.book.dto.CatalogImportData.Book;
import com.shortsb.book.dto.CatalogImportData.Card;
import com.shortsb.book.dto.CatalogImportData.Model;
import com.shortsb.book.dto.CatalogImportData.Page;
import com.shortsb.book.dto.CatalogImportData.Vector;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
// 본문 출력 없이 원본 매핑·해시·모델·벡터 파일 검증
public class CatalogImportReader {

    private static final Set<String> CARD_CODES = Set.of("WARM", "HUMOR", "THOUGHTFUL");
    private static final List<String> MODEL_FIELDS = List.of("modelId", "revision", "dimension", "normalized",
            "maxTokens", "preprocessingVersion", "encoder", "queryPrefix", "documentPrefix", "libraries");
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    // 벡터 경로 세 개를 모두 생략하면 도서만 검증·등록
    public CatalogImportData read(Path catalogDir, Path documentsDir, Path cardsInput, Path cardsDir) {
        try {
            require((documentsDir == null && cardsInput == null && cardsDir == null)
                    || (documentsDir != null && cardsInput != null && cardsDir != null), "벡터 경로 세 개를 함께 지정해주세요.");
            JsonNode manifest = json(catalogDir.resolve("manifest.json"), 1_048_576);
            require(integer(manifest, "schemaVersion") == 1
                    && "provided-pages-utf16-v1".equals(text(manifest, "mappingVersion"))
                    && integer(manifest, "pageIndexBase") == 1 && integer(manifest, "chapterIndexBase") == 0
                    && "UTF-16".equals(text(manifest, "positionUnit"))
                    && manifest.path("endPositionExclusive").isBoolean()
                    && manifest.path("endPositionExclusive").booleanValue(), "페이지 매핑 버전을 확인해주세요.");
            String sourceHash = hashField(manifest, "sourceSHA256");
            JsonNode catalog = json(catalogDir.resolve("catalog-metadata.json"), 16_777_216);
            require(catalog != null && catalog.isObject(), "도서 메타데이터는 객체여야 합니다.");
            validateStrings(catalog);
            String version = text(catalog, "version");
            require(version.equals(text(manifest, "sourceVersion")), "도서 자료 버전이 다릅니다.");
            List<JsonNode> segments = lines(catalogDir.resolve("segments.jsonl"));
            require(segments.size() == integer(manifest, "segmentCount")
                    && segments.size() == integer(manifest, "pageCount"), "검색 구간·페이지 개수가 다릅니다.");
            List<Book> books = books(catalog, segments);
            require(books.size() == integer(manifest, "bookCount"), "작품 개수가 다릅니다.");
            if (documentsDir == null) {
                return new CatalogImportData(version, sourceHash, books, null, List.of(), List.of());
            }
            JsonNode documentsManifest = json(documentsDir.resolve("manifest.json"), 1_048_576);
            Model model = model(documentsManifest, "document");
            JsonNode cardsManifest = json(cardsDir.resolve("manifest.json"), 1_048_576);
            Model cardsModel = model(cardsManifest, "query");
            require(model.equals(cardsModel), "본문·카드의 모델 설정이 다릅니다.");
            List<Vector> documents = vectors(segments, documentsDir, documentsManifest, model, "document");
            List<JsonNode> queries = lines(cardsInput);
            List<Vector> cardVectors = vectors(queries, cardsDir, cardsManifest, model, "query");
            List<Card> cards = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < queries.size(); i++) {
                JsonNode query = queries.get(i);
                require(keys(query).equals(Set.of("id", "cardCode", "text", "contentHash")), "카드 입력 필드를 확인해주세요.");
                String code = text(query, "cardCode");
                require(CARD_CODES.contains(code) && codes.add(code), "중복 또는 잘못된 카드 코드입니다.");
                String queryText = validText(query);
                String contentHash = hashField(query, "contentHash");
                require(sha256(queryText).equals(contentHash), "카드 검색 문장 해시가 다릅니다.");
                cards.add(new Card(code, text(query, "id"), queryText, contentHash, cardVectors.get(i).values()));
            }
            require(codes.equals(CARD_CODES), "고정 카드 3종이 모두 필요합니다.");
            return new CatalogImportData(version, sourceHash, books, model, documents, List.copyOf(cards));
        } catch (IOException exception) {
            throw invalid("입력 파일 경로·UTF-8 인코딩을 확인해주세요.");
        } catch (tools.jackson.core.JacksonException exception) {
            throw invalid("입력 JSON 형식을 확인해주세요.");
        }
    }

    // 현재 매핑은 표시 페이지 하나에 검색 구간 하나. 다른 매핑은 명시적으로 거절
    private List<Book> books(JsonNode catalog, List<JsonNode> segments) {
        require(catalog.path("books").isArray() && !catalog.path("books").isEmpty(), "작품 목록이 필요합니다.");
        require(integer(catalog, "bookCount") == catalog.path("books").size(), "작품 개수가 다릅니다.");
        Map<String, List<JsonNode>> byBook = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode segment : segments) {
            require(keys(segment).equals(Set.of("id", "bookId", "segmentId", "pageNo", "startPosition",
                    "endPosition", "text", "contentHash")), "검색 구간 입력 필드를 확인해주세요.");
            String id = text(segment, "id");
            require(id.length() <= 128 && ids.add(id) && id.equals(text(segment, "segmentId")), "검색 구간 ID가 다르거나 중복됩니다.");
            byBook.computeIfAbsent(text(segment, "bookId"), key -> new ArrayList<>()).add(segment);
        }
        List<Book> books = new ArrayList<>();
        Set<String> bookIds = new HashSet<>();
        for (JsonNode metadata : catalog.path("books")) {
            require(metadata.isObject() && !metadata.has("text") && !metadata.has("pages"), "도서 메타데이터에 본문을 넣을 수 없습니다.");
            String externalId = text(metadata, "id");
            require(externalId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") && bookIds.add(externalId), "작품 ID 형식·중복을 확인해주세요.");
            List<JsonNode> rows = byBook.remove(externalId);
            int count = integer(metadata, "pageCount");
            require(count > 0 && rows != null && rows.size() == count, "작품별 페이지 수가 다릅니다.");
            List<Page> pages = new ArrayList<>();
            String joiner = string(metadata, "pageJoiner");
            StringBuilder fullText = new StringBuilder();
            for (int index = 0; index < count; index++) {
                JsonNode row = rows.get(index);
                String content = validText(row);
                String hash = hashField(row, "contentHash");
                int number = index + 1;
                require(integer(row, "pageNo") == number && integer(row, "startPosition") == 0
                        && integer(row, "endPosition") == content.length() && sha256(content).equals(hash)
                        && text(row, "id").equals(externalId + ":p" + number + ":" + hash.substring(0, 16)),
                        "원본 페이지·UTF-16 위치·구간 해시가 다릅니다.");
                if (index > 0) {
                    fullText.append(joiner);
                }
                fullText.append(content);
                pages.add(new Page(number, text(row, "id"), content, hash));
            }
            String textHash = hashField(metadata, "textSHA256");
            require(sha256(fullText.toString()).equals(textHash), "페이지 재결합 본문 해시가 다릅니다.");
            chapters(metadata.path("chapters"), count);
            String title = text(metadata, "title");
            String author = text(metadata, "author");
            require(title.length() <= 255 && author.length() <= 150, "제목·저자 길이를 확인해주세요.");
            String language = text(metadata, "language");
            require(language.length() <= 35, "언어 코드 길이를 확인해주세요.");
            books.add(new Book(externalId, title, author, text(metadata, "edition"), language,
                    text(metadata, "sourceURL"), textHash, joiner, metadata, List.copyOf(pages)));
        }
        require(byBook.isEmpty(), "원본 목록에 없는 작품의 검색 구간입니다.");
        return List.copyOf(books);
    }

    // 원본 챕터의 0-based 끝 제외 범위와 포함 관계 보존
    private void chapters(JsonNode chapters, int pageCount) {
        require(chapters.isArray(), "챕터 목록을 확인해주세요.");
        Set<String> ids = new HashSet<>();
        List<Integer> parents = new ArrayList<>();
        int lastStart = -1;
        for (JsonNode chapter : chapters) {
            require(ids.add(text(chapter, "id")), "챕터 ID가 중복됩니다.");
            text(chapter, "title");
            require(chapter.path("available").isBoolean(), "챕터 제공 여부를 확인해주세요.");
            int start = integer(chapter, "start");
            int end = integer(chapter, "end");
            require(start >= 0 && start >= lastStart && start < end && end <= pageCount, "챕터 페이지 범위가 잘못됐습니다.");
            while (!parents.isEmpty() && start >= parents.getLast()) {
                parents.removeLast();
            }
            require(parents.isEmpty() || end <= parents.getLast(), "챕터 범위가 부분적으로 겹칩니다.");
            parents.add(end);
            lastStart = start;
            if (chapter.has("pageIds")) {
                JsonNode pageIds = chapter.path("pageIds");
                require(pageIds.isArray() && pageIds.size() == end - start, "챕터 페이지 ID 개수가 다릅니다.");
                Set<String> seen = new HashSet<>();
                for (JsonNode id : pageIds) {
                    require(id.isString() && !id.stringValue().isBlank() && seen.add(id.stringValue()), "챕터 페이지 ID가 잘못됐습니다.");
                }
            }
        }
    }

    // Python의 기본 ensure_ascii=True·sort_keys=True·공백 없는 JSON과 동일한 설정 지문
    private Model model(JsonNode manifest, String role) {
        require(integer(manifest, "schemaVersion") == 1 && role.equals(text(manifest, "role"))
                && integer(manifest, "recordCount") > 0, "임베딩 manifest 형식을 확인해주세요.");
        require(manifest.path("gpuReady").isBoolean() && manifest.path("gpuReady").booleanValue()
                && text(manifest, "device").matches("cuda(?::[0-9]+)?"), "GPU에서 생성한 벡터가 필요합니다.");
        String modelId = text(manifest, "modelId");
        String revision = text(manifest, "revision");
        int dimension = integer(manifest, "dimension");
        require(revision.matches("[0-9a-f]{40}") && dimension > 0
                && manifest.path("normalized").isBoolean() && manifest.path("normalized").booleanValue()
                && integer(manifest, "maxTokens") > 0, "모델 차원·revision·정규화를 확인해주세요.");
        text(manifest, "preprocessingVersion");
        text(manifest, "encoder");
        string(manifest, "queryPrefix");
        string(manifest, "documentPrefix");
        JsonNode libraries = manifest.path("libraries");
        require(keys(libraries).equals(Set.of("torch", "sentence-transformers", "transformers", "numpy")), "라이브러리 버전이 필요합니다.");
        for (String key : keys(libraries)) {
            text(libraries, key);
        }
        ObjectNode configuration = mapper.createObjectNode();
        for (String key : MODEL_FIELDS) {
            require(manifest.has(key), "모델 설정 필드가 누락됐습니다.");
            configuration.set(key, manifest.get(key));
        }
        String fingerprint = hashField(manifest, "fingerprint");
        require(fingerprint.equals(sha256(canonicalJson(configuration))), "모델 설정 지문이 다릅니다.");
        return new Model(fingerprint, modelId, revision, dimension, configuration);
    }

    // 벡터 행의 ID·순서·원본 참조까지 입력과 정확히 대조
    private List<Vector> vectors(List<JsonNode> input, Path directory, JsonNode manifest, Model model, String role)
            throws IOException {
        List<JsonNode> rows = lines(directory.resolve("vectors.jsonl"));
        require(rows.size() == input.size() && rows.size() == integer(manifest, "recordCount"), "입력·벡터·manifest 개수가 다릅니다.");
        List<Vector> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            JsonNode source = input.get(i);
            JsonNode row = rows.get(i);
            Set<String> expectedKeys = new HashSet<>(keys(source));
            expectedKeys.remove("text");
            expectedKeys.addAll(Set.of("role", "embedding"));
            require(keys(row).equals(expectedKeys) && role.equals(text(row, "role")), "벡터 행 필드·역할이 다릅니다.");
            for (String key : keys(source)) {
                if (!key.equals("text")) {
                    require(source.get(key).equals(row.get(key)), "벡터 원본 ID·위치·해시가 다릅니다.");
                }
            }
            String id = text(row, "id");
            require(id.length() <= 128 && ids.add(id), "벡터 ID 형식·중복을 확인해주세요.");
            JsonNode values = row.path("embedding");
            require(values.isArray() && values.size() == model.dimension(), "벡터 차원이 다릅니다.");
            List<Double> vector = new ArrayList<>();
            double sum = 0;
            for (JsonNode value : values) {
                require(value.isNumber(), "벡터는 유한한 실수여야 합니다.");
                double number = value.doubleValue();
                require(Double.isFinite(number), "벡터는 유한한 실수여야 합니다.");
                sum += number * number;
                vector.add(number);
            }
            require(Double.isFinite(sum) && Math.abs(Math.sqrt(sum) - 1.0) <= 1e-3, "L2 정규화된 벡터가 필요합니다.");
            result.add(new Vector(id, List.copyOf(vector)));
        }
        return List.copyOf(result);
    }

    private JsonNode json(Path path, long maxBytes) throws IOException {
        require(Files.size(path) <= maxBytes, "JSON 파일 크기 상한을 초과했습니다.");
        JsonNode node = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        require(node != null && node.isObject(), "JSON 객체가 필요합니다.");
        return node;
    }

    private List<JsonNode> lines(Path path) throws IOException {
        List<JsonNode> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            // ponytail: 현재 864개 구간은 메모리에 검증. 자료 확대 시 스트리밍 묶음으로 전환
            StringBuilder line = new StringBuilder();
            int value;
            while ((value = reader.read()) != -1) {
                if (value == '\n') {
                    addLine(rows, line);
                } else {
                    require(line.length() < 1_048_576, "JSONL 한 행의 크기 상한을 초과했습니다.");
                    line.append((char) value);
                }
            }
            addLine(rows, line);
        }
        require(!rows.isEmpty(), "JSONL 입력이 비어 있습니다.");
        return rows;
    }

    private void addLine(List<JsonNode> rows, StringBuilder line) {
        if (!line.toString().isBlank()) {
            JsonNode node = mapper.readTree(line.toString());
            require(node != null && node.isObject(), "JSONL 행은 객체여야 합니다.");
            rows.add(node);
        }
        line.setLength(0);
    }

    private String validText(JsonNode node) {
        String value = text(node, "text");
        require(value.codePointCount(0, value.length()) <= 32768
                && StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.indexOf('\0') < 0,
                "본문·검색 문장 인코딩·길이를 확인해주세요.");
        return value;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }

    String canonicalJson(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> sorted = new LinkedHashMap<>();
            node.properties().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            List<String> fields = new ArrayList<>();
            sorted.forEach((key, value) -> fields.add(asciiJsonString(key) + ":" + canonicalJson(value)));
            return "{" + String.join(",", fields) + "}";
        }
        if (node.isString()) {
            return asciiJsonString(node.stringValue());
        }
        return mapper.writeValueAsString(node);
    }

    private String asciiJsonString(String value) {
        StringBuilder ascii = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            switch (unit) {
                case '"' -> ascii.append("\\\"");
                case '\\' -> ascii.append("\\\\");
                case '\b' -> ascii.append("\\b");
                case '\f' -> ascii.append("\\f");
                case '\n' -> ascii.append("\\n");
                case '\r' -> ascii.append("\\r");
                case '\t' -> ascii.append("\\t");
                default -> {
                    if (unit < 0x20 || unit > 0x7e) {
                        ascii.append("\\u").append(String.format("%04x", (int) unit));
                    } else {
                        ascii.append(unit);
                    }
                }
            }
        }
        return ascii.append('"').toString();
    }

    // JSONB 저장 불가능한 NUL·고립 서로게이트를 메타데이터에서도 사전 거절
    private void validateStrings(JsonNode node) {
        if (node.isString()) {
            String value = node.stringValue();
            require(StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.indexOf('\0') < 0,
                    "메타데이터 문자열 인코딩을 확인해주세요.");
        } else if (node.isObject()) {
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                require(StandardCharsets.UTF_8.newEncoder().canEncode(key) && key.indexOf('\0') < 0,
                        "메타데이터 필드 이름을 확인해주세요.");
                validateStrings(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::validateStrings);
        }
    }

    private static Set<String> keys(JsonNode node) {
        require(node.isObject(), "JSON 객체가 필요합니다.");
        Set<String> keys = new HashSet<>();
        node.properties().forEach(entry -> keys.add(entry.getKey()));
        return keys;
    }

    private static String hashField(JsonNode node, String key) {
        String value = text(node, key);
        require(value.matches("[0-9a-f]{64}"), "SHA-256 형식을 확인해주세요.");
        return value;
    }

    private static int integer(JsonNode node, String key) {
        JsonNode value = node.path(key);
        require(value.isIntegralNumber() && value.canConvertToInt(), "정수 필드 형식·범위를 확인해주세요.");
        return value.intValue();
    }

    private static String string(JsonNode node, String key) {
        JsonNode value = node.path(key);
        require(value.isString(), "문자열 필드 형식을 확인해주세요.");
        String text = value.stringValue();
        require(StandardCharsets.UTF_8.newEncoder().canEncode(text) && text.indexOf('\0') < 0, "문자열 인코딩을 확인해주세요.");
        return text;
    }

    private static String text(JsonNode node, String key) {
        String value = string(node, key);
        require(!value.isBlank(), "필수 문자열이 비어 있습니다.");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
