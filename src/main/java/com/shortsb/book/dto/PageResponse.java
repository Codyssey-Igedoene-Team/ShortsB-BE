package com.shortsb.book.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "분할을 변경하지 않은 원본 표시 페이지")
// 표시 페이지 번호와 실제 본문 응답
public record PageResponse(
        @Schema(description = "1부터 시작하는 페이지 번호", example = "1") int pageNo,
        @Schema(description = "해당 페이지 본문. 생성된 요약이 아닌 저장 원문") String content) {

    @Override
    public String toString() {
        return "PageResponse[pageNo=" + pageNo + "]";
    }
}
