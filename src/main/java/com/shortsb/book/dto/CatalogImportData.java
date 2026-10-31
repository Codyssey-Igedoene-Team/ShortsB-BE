package com.shortsb.book.dto;

import java.util.List;
import tools.jackson.databind.JsonNode;

// 파일 검증을 마친 도서·페이지·벡터 등록 묶음
public record CatalogImportData(String version, String sourceHash, List<Book> books,
        Model model, List<Vector> documents, List<Card> cards) {

    public int pageCount() {
        return books.stream().mapToInt(book -> book.pages().size()).sum();
    }

    // 본문을 제외한 원본 메타데이터와 표시 페이지
    public record Book(String externalId, String title, String author, String edition, String language,
            String sourceUrl, String textHash, String pageJoiner, JsonNode metadata, List<Page> pages) {}

    // 현재 자료의 페이지 전체와 동일한 검색 구간. 끝 위치는 UTF-16 길이
    public record Page(int number, String segmentId, String content, String contentHash) {
        @Override
        public String toString() {
            return "Page[number=" + number + "]";
        }
    }

    // Python의 설정 지문 대상 필드와 등록 이력용 모델 정보
    public record Model(String fingerprint, String modelId, String revision, int dimension,
            JsonNode configuration) {}

    // 안정 ID로 원본에 연결한 정규화 벡터
    public record Vector(String inputId, List<Double> values) {}

    // ANY를 제외한 고정 카드 검색 문장과 query 벡터
    public record Card(String code, String inputId, String text, String contentHash, List<Double> values) {
        @Override
        public String toString() {
            return "Card[code=" + code + "]";
        }
    }
}
