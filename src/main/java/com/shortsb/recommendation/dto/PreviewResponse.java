package com.shortsb.recommendation.dto;

import com.shortsb.book.dto.BookResponse;
import com.shortsb.book.dto.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "첫 발췌 결과. 요청 쪽수와 실제 제공 쪽수를 구분하며 독서 진행률에는 합산하지 않음")
// 승인 작품 정보와 원본 연속 페이지 응답
public record PreviewResponse(
        @Schema(description = "세션에 보관된 발췌 UUID. 조회와 후속 대비 참조에 사용") String previewId,
        @Schema(description = "도서·출처·번역 안내") BookResponse book,
        @Schema(description = "원래 요청 쪽수. 후속 대비에도 같은 값 적용", example = "10") int requestedPageCount,
        @Schema(description = "실제 제공 쪽수. min(요청,작품 전체)", example = "8") int actualPageCount,
        @Schema(description = "발췌 시작 페이지. 1부터 시작", example = "1") int startPageNo,
        @Schema(description = "발췌 끝 페이지. 포함", example = "8") int endPageNo,
        @Schema(description = "요청 시 선택한 카드") StoryType storyType,
        @Schema(description = "RANDOM, CARD, PROMPT, CARD_AND_PROMPT 중 추천 경로") String recommendationType,
        @Schema(description = "벡터 검색으로 선택한 기준 페이지. RANDOM이면 null") Integer matchedPageNo,
        @Schema(description = "실제 추천 방식 안내. 생성형 본문 해석이 아닌 고정 설명") String reason,
        @Schema(description = "시작~끝의 순서대로 제공하는 원본 페이지") List<PageResponse> pages) {}
