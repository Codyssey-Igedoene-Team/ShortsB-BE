package com.shortsb.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@Schema(description = "첫 추천 요청. 쪽수·카드는 필수이며 프롬프트는 선택")
// 첫 발췌 입력과 공백 프롬프트 정규화
public record PreviewRequest(
        @NotNull @Min(1) @Max(10)
        @Schema(description = "보고 싶은 쪽수. 1~10 정수", type = "integer", format = "int32", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer requestedPageCount,
        @NotNull
        @Schema(description = "이야기 카드 하나. ANY도 명시적으로 선택", implementation = StoryType.class, example = "WARM", requiredMode = Schema.RequiredMode.REQUIRED)
        StoryType storyType,
        @Size(max = 2000)
        @Schema(description = "선택 자유 프롬프트. UTF-16 2,000단위 이하. 공백만 있으면 입력 없음", type = "string", example = "다시 시작할 용기를 얻고 싶어요")
        String prompt) {

    // 숫자 문자열·소수 쪽수·비문자열 프롬프트의 암묵 변환 차단
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static PreviewRequest fromJson(@JsonProperty("requestedPageCount") JsonNode count,
            @JsonProperty("storyType") JsonNode type, @JsonProperty("prompt") JsonNode prompt) {
        if ((count != null && !count.isNull() && (!count.isIntegralNumber() || !count.canConvertToInt()))
                || (type != null && !type.isNull() && !type.isString())
                || (prompt != null && !prompt.isNull() && !prompt.isString())) {
            throw new IllegalArgumentException("쪽수는 정수, 카드·프롬프트는 문자열이어야 합니다.");
        }
        return new PreviewRequest(count == null || count.isNull() ? null : count.intValue(),
                type == null || type.isNull() ? null : StoryType.valueOf(type.stringValue()),
                prompt == null || prompt.isNull() ? null : prompt.stringValue());
    }

    public String normalizedPrompt() {
        return prompt == null || prompt.codePoints().allMatch(value -> Character.isWhitespace(value) || Character.isSpaceChar(value))
                ? null : prompt;
    }

    @Override
    public String toString() {
        return "PreviewRequest[requestedPageCount=" + requestedPageCount + ", storyType=" + storyType + "]";
    }
}
