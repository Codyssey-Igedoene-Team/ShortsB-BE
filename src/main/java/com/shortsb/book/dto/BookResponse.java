package com.shortsb.book.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "제공 승인을 받은 작품의 정보와 원본 출처 안내")
// 본문을 제외한 승인 도서 응답
public record BookResponse(
        @Schema(description = "서비스 내부 도서 ID. 문자열", example = "1") String bookId,
        @Schema(description = "작품 제목") String title,
        @Schema(description = "저자 표시명") String author,
        @Schema(description = "서비스 전체 쪽수. 원본 pages 개수", example = "8") int totalPageCount,
        @Schema(description = "확보한 판본 식별값") String edition,
        @Schema(description = "판본 표시 안내") String editionLabel,
        @Schema(description = "본문 언어 코드", example = "ko") String language,
        @Schema(description = "원본 자료의 작품 유형") String workType,
        @Schema(description = "본문 출처 이름") String sourceName,
        @Schema(description = "본문 출처 URL") String sourceUrl,
        @Schema(description = "이용 조건 URL. 없으면 null") String licenseUrl,
        @Schema(description = "이용 조건 표시. 없으면 null") String licenseLabel,
        @Schema(description = "출처·기여자 표시. 없으면 null") String credits,
        @Schema(description = "AI 번역·교열 전 등 원본 자료의 안내. 원문 그대로 제공") String translationNote) {}
