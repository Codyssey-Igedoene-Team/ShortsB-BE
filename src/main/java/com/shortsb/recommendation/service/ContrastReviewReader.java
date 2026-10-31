package com.shortsb.recommendation.service;

import com.shortsb.recommendation.dto.ContrastAxis;
import com.shortsb.recommendation.dto.ContrastReview;
import com.shortsb.recommendation.dto.ContrastReview.Evidence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
// 검수 JSONL 구조 검사. 인용 구절은 오류·로그에 출력하지 않음
public class ContrastReviewReader {

    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public List<ContrastReview> read(Path input) {
        try {
            require(Files.size(input) <= 16_777_216);
            List<ContrastReview> rows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            try (var stream = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
                String line;
                while ((line = stream.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    require(line.length() <= 1_048_576 && rows.size() < 10000);
                    JsonNode node = mapper.readTree(line);
                    require(keys(node).equals(Set.of("sourceBookId", "targetBookId", "axis", "commonTopic", "reason",
                            "sourceContentHash", "targetContentHash", "sourceEvidence", "targetEvidence", "reviewStatus", "reviewedBy")));
                    String source = text(node, "sourceBookId", true, 64);
                    String target = text(node, "targetBookId", true, 64);
                    require(source.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                            && target.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") && !source.equals(target));
                    ContrastAxis axis = ContrastAxis.fromCode(text(node, "axis", true, 30));
                    require(ids.add(source + ":" + target + ":" + axis.code()));
                    String status = text(node, "reviewStatus", true, 20);
                    require(Set.of("pending", "approved", "rejected").contains(status));
                    boolean approved = status.equals("approved");
                    String reviewer = text(node, "reviewedBy", !status.equals("pending"), 100);
                    String sourceHash = hash(node, "sourceContentHash", approved);
                    String targetHash = hash(node, "targetContentHash", approved);
                    rows.add(new ContrastReview(source, target, axis, text(node, "commonTopic", approved, 1000),
                            text(node, "reason", approved, 4000), sourceHash, targetHash,
                            evidence(node.path("sourceEvidence"), approved), evidence(node.path("targetEvidence"), approved),
                            status, reviewer));
                }
            }
            require(!rows.isEmpty());
            return List.copyOf(rows);
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("검수 JSONL 경로·인코딩·형식을 확인해주세요.");
        }
    }

    private Evidence evidence(JsonNode node, boolean required) {
        if (node.isNull() || node.isMissingNode()) {
            require(!required);
            return null;
        }
        require(keys(node).equals(Set.of("startPageNo", "endPageNo", "quote")));
        JsonNode start = node.path("startPageNo");
        JsonNode end = node.path("endPageNo");
        require(start.isIntegralNumber() && start.canConvertToInt() && end.isIntegralNumber() && end.canConvertToInt());
        int first = start.intValue();
        int last = end.intValue();
        require(first > 0 && last >= first && (long) last - first < 10);
        return new Evidence(first, last, text(node, "quote", true, 32768));
    }

    private String hash(JsonNode node, String key, boolean required) {
        String value = text(node, key, required, 64);
        require(value == null || value.matches("[0-9a-f]{64}"));
        return value;
    }

    private String text(JsonNode node, String key, boolean required, int maximum) {
        JsonNode field = node.path(key);
        if (field.isNull() || field.isMissingNode()) {
            require(!required);
            return null;
        }
        require(field.isString());
        String value = field.stringValue();
        require(value.length() <= maximum && StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.indexOf('\0') < 0);
        if (value.isBlank()) {
            require(!required);
            return null;
        }
        return value;
    }

    private Set<String> keys(JsonNode node) {
        require(node != null && node.isObject());
        Set<String> keys = new HashSet<>();
        node.properties().forEach(entry -> keys.add(entry.getKey()));
        return keys;
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("대비 검수 자료의 형식·범위·필수 항목을 확인해주세요.");
        }
    }
}
