package com.shortsb.user.dto;

import com.shortsb.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 세션으로 인증된 회원 정보")
// 본인 정보 조회 응답
public record MeResponse(
        @Schema(description = "서비스 내부 회원 ID. JavaScript 정밀도 손실 방지를 위한 문자열", example = "1")
        String id,
        @Schema(description = "구글에서 확인한 이메일", example = "reader@example.com")
        String email,
        @Schema(description = "표시 이름. 등록된 이름이 없으면 null", example = "독자")
        String nickname) {

    // 회원 엔티티를 문자열 ID의 응답으로 변환
    public static MeResponse from(User user) {
        return new MeResponse(user.getId().toString(), user.getEmail(), user.getNickname());
    }
}
