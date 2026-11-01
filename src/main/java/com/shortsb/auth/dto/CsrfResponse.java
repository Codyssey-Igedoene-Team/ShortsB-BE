package com.shortsb.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상태 변경 요청에 사용할 세션 기반 CSRF 토큰")
// CSRF 토큰과 요청 헤더 이름 응답
public record CsrfResponse(
        @Schema(description = "Execute 후 실제 응답에서 복사할 토큰. 로그인·로그아웃 후 다시 조회한다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(description = "토큰을 전달할 HTTP 헤더 이름", example = "X-CSRF-TOKEN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String headerName) {
}
