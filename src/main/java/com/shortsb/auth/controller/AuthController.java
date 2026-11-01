package com.shortsb.auth.controller;

import com.shortsb.auth.dto.CsrfResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증", description = "구글 로그인 후 같은 브라우저의 세션 쿠키로 테스트한다. CSRF 조회 후 로그아웃을 실행한다.")
// 인증 보조 API
public class AuthController {

    // 상태 변경 요청에 사용할 CSRF 토큰 조회 API
    @Operation(summary = "CSRF 토큰 조회", description = """
            로그인 전에도 호출할 수 있습니다. Try it out → Execute 후 실제 응답의 token을 복사하세요.
            로그아웃 요청의 X-CSRF-TOKEN 입력란에 붙여넣습니다.
            로그인·로그아웃 이후에는 기존 토큰을 재사용하지 말고 이 API를 다시 호출하세요.
            세션 쿠키는 브라우저가 전달하며 별도 요청값은 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "현재 세션의 토큰과 헤더 이름. 응답은 캐시하지 않습니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CsrfResponse.class)))
    @GetMapping("/api/csrf")
    public ResponseEntity<CsrfResponse> csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }
}
