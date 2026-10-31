package com.shortsb.recommendation.dto;

import com.shortsb.book.dto.BookResponse;
import com.shortsb.book.dto.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "첫 발췌와 연결된 대비 결과. 후보 없음도 정상 200 응답")
// 원래 발췌 참조와 승인 근거 기반 두 번째 작품 응답
public record ContrastResponse(
        @Schema(description = "대비 제공 가능 여부") boolean available,
        @Schema(description = "첫 발췌 UUID") String sourcePreviewId,
        @Schema(description = "없으면 NO_ELIGIBLE_CONTRAST, 성공이면 null") String reasonCode,
        @Schema(description = "대비 결과. 후보가 없으면 null") Result contrast) {

    public record Result(
            @Schema(description = "승인된 대비 관계 ID. 문자열") String relationId,
            @Schema(description = "texture/perspective/tone/attitude/ending/setting", type = "string") ContrastAxis contrastAxis,
            @Schema(description = "대비 축의 한국어 표시") String contrastAxisLabel,
            @Schema(description = "사람이 검수한 공통 주제") String commonTopic,
            @Schema(description = "양쪽 실제 근거로 검수한 대비 설명") String reason,
            @Schema(description = "원래 요청 쪽수") int requestedPageCount,
            @Schema(description = "대비 작품 실제 제공 쪽수") int actualPageCount,
            @Schema(description = "대비 작품. bookId로 전체 독서 시작 가능") BookResponse book,
            @Schema(description = "대비 발췌 시작 페이지") int startPageNo,
            @Schema(description = "대비 발췌 끝 페이지. 포함") int endPageNo,
            @Schema(description = "첫 발췌 내부의 승인 근거") EvidenceReference sourceEvidence,
            @Schema(description = "두 번째 발췌 내부의 승인 근거") EvidenceReference targetEvidence,
            @Schema(description = "대비 작품의 원본 연속 페이지") List<PageResponse> pages) {}

    public record EvidenceReference(
            @Schema(description = "내부 작품 ID. 문자열") String bookId,
            @Schema(description = "근거 첫 페이지") int startPageNo,
            @Schema(description = "근거 마지막 페이지. 포함") int endPageNo,
            @Schema(description = "지정 범위에 실제 포함된 검수 구절") String quote) {
        @Override
        public String toString() {
            return "EvidenceReference[bookId=" + bookId + ", startPageNo=" + startPageNo + ", endPageNo=" + endPageNo + "]";
        }
    }
}
