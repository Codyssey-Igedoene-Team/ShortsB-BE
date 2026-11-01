package com.shortsb.recommendation.service;

import com.shortsb.recommendation.dto.ContrastResponse;
import com.shortsb.recommendation.dto.PreviewState;

// 첫 발췌와 검수 근거를 연결한 대비 추천 계약
public interface ContrastService {

    ContrastResponse recommend(PreviewState source);
}
