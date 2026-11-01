package com.shortsb.reading.dto;

import com.shortsb.book.dto.BookResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "책 전체 독서 기록. 현재 위치는 재개용, 최대 위치는 진행률 계산용")
// 본문 없는 전체 독서 상태 응답
public record ReadingResponse(
        @Schema(description = "전체 독서 세션 ID. 문자열", example = "1") String sessionId,
        @Schema(description = "승인 도서와 전체 쪽수·출처") BookResponse book,
        @Schema(description = "재개할 현재 페이지. 1-based", example = "1") int currentPageNo,
        @Schema(description = "현재 페이지 내 UTF-16 위치", example = "0") int currentPosition,
        @Schema(description = "최대 도달 페이지") int furthestPageNo,
        @Schema(description = "최대 도달 페이지 내 UTF-16 위치") int furthestPosition,
        @Schema(description = "최대 본문 위치 기준 0~100. 소수 둘째 자리까지 내림", example = "12.34") BigDecimal progressPercent,
        @Schema(description = "reading / paused / completed") String status,
        @Schema(description = "저장 버전. 변경 요청에 그대로 전달할 문자열", example = "0") String version,
        @Schema(description = "전체 독서 시작 시각") Instant startedAt,
        @Schema(description = "마지막 저장 시각") Instant updatedAt,
        @Schema(description = "전체 완료 시각. 미완료면 null") Instant completedAt) {}
