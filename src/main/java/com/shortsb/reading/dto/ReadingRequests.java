package com.shortsb.reading.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shortsb.common.exception.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

// 전체 독서 입력과 정수·문자열의 엄격한 JSON 변환
public final class ReadingRequests {

    private ReadingRequests() {}

    @Schema(description = "책 전체 독서 시작. 발췌 범위는 입력하지 않음")
    public record Start(@NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
            @Schema(description = "발췌 응답의 book.bookId. 문자열", example = "1", type = "string") String bookId) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static Start fromJson(@JsonProperty("bookId") JsonNode id) {
            return new Start(string(id));
        }
    }

    @Schema(description = "현재 위치와 상태 저장. 조회한 version을 그대로 전달")
    public record Progress(
            @NotNull @Min(1) @Schema(description = "1-based 현재 페이지", example = "1", type = "integer") Integer pageNo,
            @NotNull @Min(0) @Schema(description = "페이지 내 UTF-16 위치. 끝 위치도 허용", example = "10", type = "integer") Integer position,
            @NotBlank @Pattern(regexp = "[0-9]{1,19}") @Schema(description = "조회 응답의 version. 문자열", example = "0", type = "string") String version,
            @NotNull @Pattern(regexp = "reading|paused") @Schema(description = "읽는 중 또는 중단", allowableValues = {"reading", "paused"}, example = "reading", type = "string") String status) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static Progress fromJson(@JsonProperty("pageNo") JsonNode page, @JsonProperty("position") JsonNode position,
                @JsonProperty("version") JsonNode version, @JsonProperty("status") JsonNode status) {
            return new Progress(integer(page), integer(position), string(version), string(status));
        }
    }

    @Schema(description = "마지막 페이지 끝을 저장한 후 최신 version으로 전체 완료")
    public record Complete(@NotBlank @Pattern(regexp = "[0-9]{1,19}")
            @Schema(description = "최신 저장 응답의 version. 문자열", example = "1", type = "string") String version) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public static Complete fromJson(@JsonProperty("version") JsonNode version) {
            return new Complete(string(version));
        }
    }

    // JavaScript 정밀도 손실 방지를 위한 문자열 ID·버전 범위 검사
    public static long number(String value, boolean positive) {
        try {
            long number = Long.parseLong(value);
            if (number < 0 || (positive && number == 0)) {
                throw invalid();
            }
            return number;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static String string(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw invalid();
        }
        return node.stringValue();
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid();
        }
        return node.intValue();
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "ID·버전·위치의 형식과 범위를 확인해주세요.");
    }
}
