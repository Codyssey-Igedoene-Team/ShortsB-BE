package com.shortsb.recommendation.dto;

import java.io.Serializable;
import java.util.UUID;

// 본문 없이 세션에 보관하는 소유자·입력·원본 버전·발췌 범위
public record PreviewState(UUID previewId, long userId, long bookId, String contentHash, int totalPages,
        int requestedPageCount, int startPageNo, int endPageNo, StoryType storyType, String prompt,
        String modelFingerprint, String recommendationType, Integer matchedPageNo, String reason) implements Serializable {

    @Override
    public String toString() {
        return "PreviewState[previewId=" + previewId + "]";
    }
}
