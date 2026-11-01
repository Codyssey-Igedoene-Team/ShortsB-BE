package com.shortsb.recommendation.service;

import com.shortsb.recommendation.dto.PreviewRequest;
import com.shortsb.recommendation.dto.PreviewResponse;
import com.shortsb.recommendation.dto.PreviewState;

// 첫 추천 생성과 저장된 발췌 범위 조회 계약
public interface PreviewService {

    CreatedPreview create(long userId, PreviewRequest request);

    PreviewResponse get(PreviewState state);

    // 응답 본문과 세션 보관용 참조 분리
    record CreatedPreview(PreviewResponse response, PreviewState state) {}
}
