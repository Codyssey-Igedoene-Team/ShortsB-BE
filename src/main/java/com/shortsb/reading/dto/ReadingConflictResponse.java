package com.shortsb.reading.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "409 충돌 응답. 과거 요청의 version만 바꿔 자동 재전송하지 않음")
// 저장 충돌 코드와 최신 독서 기록
public record ReadingConflictResponse(
        @Schema(description = "READING_VERSION_CONFLICT / READING_ALREADY_COMPLETED / READING_NOT_AT_END") String code,
        @Schema(description = "충돌 안내") String message,
        @Schema(description = "클라이언트가 반영할 최신 상태") ReadingResponse current) {}
