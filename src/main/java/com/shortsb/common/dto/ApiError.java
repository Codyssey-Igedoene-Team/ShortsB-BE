package com.shortsb.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "API 공통 오류 응답")
// API 공통 오류 응답
public record ApiError(
        @Schema(description = "오류를 구분하는 코드", example = "AUTHENTICATION_REQUIRED") String code,
        @Schema(description = "사용자에게 안내할 메시지", example = "로그인이 필요합니다.") String message) {
}
