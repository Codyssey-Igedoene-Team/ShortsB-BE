package com.shortsb.user.controller;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.common.dto.ApiError;
import com.shortsb.user.dto.MeResponse;
import com.shortsb.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@Tag(name = "회원", description = "현재 로그인한 회원의 정보를 조회한다.")
// 현재 로그인 회원 조회 API
public class UserController {

    private final UserService userService;

    // 세션의 회원 ID를 사용한 본인 정보 조회 API
    @Operation(summary = "로그인한 내 정보 조회", description = """
            먼저 문서 상단의 구글 로그인 링크를 새 탭에서 열어 로그인하세요.
            같은 브라우저의 세션 쿠키로 본인 정보를 조회하며 사용자 ID나 토큰을 직접 입력하지 않습니다.
            회원 ID는 문자열입니다. 로그아웃 후 다시 Execute하면 401이 반환돼야 합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인한 회원 정보",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MeResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":"1","email":"reader@example.com","nickname":"독자"}
                                    """))),
            @ApiResponse(responseCode = "401", description = "비로그인·세션 만료 또는 삭제된 회원",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"code":"AUTHENTICATION_REQUIRED","message":"로그인이 필요합니다."}
                                    """))),
            @ApiResponse(responseCode = "500", description = "회원 조회 중 서버 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/me")
    public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userService.getMe(principal.userId());
    }
}
