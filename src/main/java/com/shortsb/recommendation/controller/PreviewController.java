package com.shortsb.recommendation.controller;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.common.dto.ApiError;
import com.shortsb.common.exception.ApiException;
import com.shortsb.recommendation.dto.PreviewRequest;
import com.shortsb.recommendation.dto.PreviewResponse;
import com.shortsb.recommendation.dto.PreviewState;
import com.shortsb.recommendation.service.PreviewService;
import com.shortsb.recommendation.service.PreviewStore;
import com.shortsb.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "추천 발췌", description = "쪽수·카드·선택 프롬프트 기반 첫 추천과 세션 발췌 재조회")
// 첫 추천 생성과 본인 세션의 원본 발췌 조회 API
public class PreviewController {

    private final PreviewService previews;
    private final PreviewStore store;
    private final UserService users;

    public PreviewController(PreviewService previews, PreviewStore store, UserService users) {
        this.previews = previews;
        this.store = store;
        this.users = users;
    }

    @Operation(summary = "첫 추천 발췌 생성", description = """
            1. 구글 로그인 후 GET /api/csrf의 실제 응답 token을 X-CSRF-TOKEN에 입력하세요.
            2. requestedPageCount(정수 1~10)와 storyType(WARM/HUMOR/THOUGHTFUL/ANY)을 입력하세요.
            3. prompt는 선택이며 UTF-16 2,000단위 이하입니다. null·빈 문자열·공백만 입력하면 프롬프트 없이 처리합니다.
            카드만은 사전 벡터, 프롬프트가 있으면 FastAPI query 벡터를 사용합니다. 함께 입력하면 기본 카드 40%·프롬프트 60%입니다.
            ANY만 선택하면 GPU 없이 작품별 균등 선택 후 첫 페이지부터 제공합니다.
            10쪽 요청에 총 8쪽 작품이면 8쪽을 반환합니다. book에 작품 전체 쪽수·출처·번역 안내가 있습니다.
            발췌는 세션에 최대 10개 보관하며 초과 시 가장 오래된 발췌가 제거됩니다. 전체 독서 진행률에는 합산하지 않습니다.
            실행 전 도서 등록·권한 승인이 필요하며 벡터 검색에는 본문/카드 벡터 등록, 프롬프트에는 GPU 서버 연결도 필요합니다.
            """, parameters = @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/csrf의 실제 응답 token. 로그인 후 새 토큰 사용", schema = @Schema(type = "string")))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "첫 발췌 생성. Location 헤더로 재조회"),
            @ApiResponse(responseCode = "400", description = "잘못된 쪽수·카드·프롬프트 또는 모델 입력 상한 초과", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요·세션 만료", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "CSRF_INVALID. 현재 세션의 토큰 재조회 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "NO_RECOMMENDATION_CANDIDATES 또는 BOOK_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "추천 처리 중 본문 버전 변경", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "임베딩 서버 응답 형식 오류", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "모델·벡터·본문 준비 부족 또는 GPU 연결/호환성 오류", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/api/previews")
    public ResponseEntity<PreviewResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples = {
                    @ExampleObject(name = "카드와 프롬프트", value = "{\"requestedPageCount\":5,\"storyType\":\"WARM\",\"prompt\":\"다시 시작할 용기를 얻고 싶어요\"}"),
                    @ExampleObject(name = "카드만", value = "{\"requestedPageCount\":10,\"storyType\":\"HUMOR\"}"),
                    @ExampleObject(name = "프롬프트만", value = "{\"requestedPageCount\":3,\"storyType\":\"ANY\",\"prompt\":\"외로움에 관한 이야기\"}"),
                    @ExampleObject(name = "무작위 작품", value = "{\"requestedPageCount\":10,\"storyType\":\"ANY\"}")
            })) @Valid @RequestBody PreviewRequest input,
            @Parameter(hidden = true) HttpServletRequest request) {
        long userId = requireUser(principal);
        PreviewService.CreatedPreview created = previews.create(userId, input);
        store.save(request.getSession(false), created.state());
        return ResponseEntity.created(URI.create("/api/previews/" + created.response().previewId()))
                .cacheControl(CacheControl.noStore()).body(created.response());
    }

    @Operation(summary = "저장된 발췌 재조회", description = """
            첫 추천 응답의 previewId를 같은 로그인 세션에서 입력하세요. GPU 재호출 없이 저장했던 범위의 현재 본문을 조회합니다.
            GET이므로 CSRF 헤더는 필요 없습니다. 다른 사용자의 발췌·보관 상한으로 제거된 발췌는 404입니다.
            로그아웃·세션 만료·서버 재시작으로 발췌가 사라지면 새 추천을 요청하세요.
            본문 버전이 변경되면 409, 작품이 미승인 상태가 되면 404이며 기존 본문을 그대로 노출하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동일 발췌 범위의 작품 정보·페이지"),
            @ApiResponse(responseCode = "400", description = "잘못된 UUID", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "PREVIEW_NOT_FOUND 또는 BOOK_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "PREVIEW_CONTENT_CHANGED. 새 추천 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "CONTENT_UNAVAILABLE. 본문 무결성 확인 필요", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/previews/{previewId}")
    public ResponseEntity<PreviewResponse> get(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @PathVariable("previewId") UUID previewId, @Parameter(hidden = true) HttpServletRequest request) {
        long userId = requireUser(principal);
        PreviewState state = store.get(request.getSession(false), userId, previewId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(previews.get(state));
    }

    private long requireUser(LoginUser principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        users.getMe(principal.userId());
        return principal.userId();
    }
}
