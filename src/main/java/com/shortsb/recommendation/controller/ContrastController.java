package com.shortsb.recommendation.controller;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.common.dto.ApiError;
import com.shortsb.common.exception.ApiException;
import com.shortsb.recommendation.dto.ContrastResponse;
import com.shortsb.recommendation.service.ContrastService;
import com.shortsb.recommendation.service.PreviewStore;
import com.shortsb.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "대비 발췌", description = "첫 발췌 안의 검수 근거와 연결되는 다른 작품 추천")
// 본인 세션의 첫 발췌를 사용하는 승인 대비 추천 API
public class ContrastController {

    private final ContrastService contrasts;
    private final PreviewStore previews;
    private final UserService users;

    public ContrastController(ContrastService contrasts, PreviewStore previews, UserService users) {
        this.contrasts = contrasts;
        this.previews = previews;
        this.users = users;
    }

    @Operation(summary = "대비 작품 발췌 추천", description = """
            1. 구글 로그인·CSRF 조회 후 첫 추천을 생성하세요.
            2. 같은 세션의 첫 previewId와 현재 X-CSRF-TOKEN을 입력하세요. 요청 본문은 없습니다.
            원래 요청 쪽수를 유지하며 양쪽 실제 근거를 담을 수 있는 승인 관계만 사용합니다.
            후보가 없으면 200·available=false·NO_ELIGIBLE_CONTRAST이며 첫 작품의 전체 독서는 계속 가능합니다.
            성공 시 contrast에 두 번째 작품·연속 페이지·검수한 공통 주제/축/이유·양쪽 근거가 있습니다.
            첫 발췌와 같은 모델의 사전 벡터를 재사용하고 ANY 단독의 첫 발췌는 활성 모델을 사용합니다.
            새 GPU 요청·두 번째 previewId 생성은 없습니다. 재요청하면 현재 승인 자료로 다시 계산합니다.
            원하는 작품의 bookId를 전체 독서 시작 API에 전달하세요. CSV 후보만 등록한 상태에서는 대비가 제공되지 않습니다.
            """, parameters = @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/csrf의 실제 응답 token", schema = @Schema(type = "string")))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대비 발췌 또는 후보 없음", content = @Content(schema = @Schema(implementation = ContrastResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 previewId 형식", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요·삭제 회원", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "현재 세션의 CSRF 토큰 확인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "PREVIEW_NOT_FOUND 또는 BOOK_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "첫 발췌 본문 변경. 새 추천 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "필요한 벡터·본문·승인 근거의 준비/무결성 오류", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/api/previews/{previewId}/contrast")
    public ResponseEntity<ContrastResponse> contrast(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @PathVariable("previewId") UUID previewId, @Parameter(hidden = true) HttpServletRequest request) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        users.getMe(principal.userId());
        var source = previews.get(request.getSession(false), principal.userId(), previewId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contrasts.recommend(source));
    }
}
