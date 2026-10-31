package com.shortsb.recommendation.dto;

// 사람이 작성한 대비 검수 입력. 양쪽 ID는 원본 자료의 외부 ID
public record ContrastReview(String sourceBookId, String targetBookId, ContrastAxis axis, String commonTopic,
        String reason, String sourceContentHash, String targetContentHash, Evidence sourceEvidence,
        Evidence targetEvidence, String reviewStatus, String reviewedBy) {

    @Override
    public String toString() {
        return "ContrastReview[axis=" + axis + ", reviewStatus=" + reviewStatus + "]";
    }

    public record Evidence(int startPageNo, int endPageNo, String quote) {
        @Override
        public String toString() {
            return "Evidence[startPageNo=" + startPageNo + ", endPageNo=" + endPageNo + "]";
        }
    }
}
