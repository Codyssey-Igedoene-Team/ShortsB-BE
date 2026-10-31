package com.shortsb.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "WARM: 따뜻한 이야기, HUMOR: 유쾌한 이야기, THOUGHTFUL: 생각할 이야기, ANY: 무엇이든 좋아요")
// 필수 이야기 카드. ANY는 벡터 없는 분위기 제한 해제
public enum StoryType {
    WARM, HUMOR, THOUGHTFUL, ANY
}
