package com.shortsb.reading.controller;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.book.dto.PageResponse;
import com.shortsb.common.dto.ApiError;
import com.shortsb.common.exception.ApiException;
import com.shortsb.reading.dto.ReadingConflictResponse;
import com.shortsb.reading.dto.ReadingRequests;
import com.shortsb.reading.dto.ReadingResponse;
import com.shortsb.reading.service.ReadingService;
import com.shortsb.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reading-sessions")
@Tag(name = "전체 독서", description = "승인 도서 전체 읽기·현재/최대 위치 저장·재개·완료")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력 형식·페이지·위치 오류", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요·삭제된 회원", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "상태 변경 요청의 CSRF 토큰 누락·위조", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "없는/타 회원 독서 기록·미승인 도서·없는 페이지", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "버전/완료 상태 충돌은 current 포함. 본문 변경은 code/message만 반환",
                content = @Content(schema = @Schema(oneOf = {ReadingConflictResponse.class, ApiError.class}))),
        @ApiResponse(responseCode = "503", description = "본문·저장 위치 데이터 무결성 확인 필요", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
// 로그인 회원의 전체 독서 API. 원본 본문은 페이지 단위 제공
public class ReadingController {

    private final ReadingService reading;
    private final UserService users;

    public ReadingController(ReadingService reading, UserService users) {
        this.reading = reading;
        this.users = users;
    }

    @Operation(summary = "전체 독서 시작 또는 기존 기록 재개", description = """
            구글 로그인 후 GET /api/csrf의 실제 token을 X-CSRF-TOKEN에 입력하세요.
            발췌 응답의 book.bookId를 문자열로 전달합니다. 예: {"bookId":"1"}
            새 기록은 1페이지·위치 0·version "0"으로 시작합니다. 기존 미완료 기록이 있으면 위치·상태·버전을 유지해 반환합니다.
            완료한 책을 다시 선택하면 새 기록을 만듭니다. 발췌 쪽수는 전체 진행량에 합산하지 않습니다.
            시작 후 페이지 조회는 별도 호출하고, 실제 재개 상태는 progress에 reading으로 저장하세요.
            """, parameters = @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/csrf 실제 응답 token", schema = @Schema(type = "string")))
    @ApiResponses({@ApiResponse(responseCode = "201", description = "새 전체 독서 기록"),
            @ApiResponse(responseCode = "200", description = "기존 미완료 기록 재사용")})
    @PostMapping
    public ResponseEntity<ReadingResponse> start(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @Valid @RequestBody ReadingRequests.Start input) {
        ReadingService.Started started = reading.start(user(principal), ReadingRequests.number(input.bookId(), true));
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/reading-sessions/" + started.response().sessionId()))
                .cacheControl(CacheControl.noStore()).body(started.response());
    }

    @Operation(summary = "최근 미완료 독서 조회", description = """
            로그인 후 호출합니다. GET에는 CSRF 헤더가 필요 없습니다.
            bookId를 생략하면 최근 저장한 미완료 기록 하나를, 지정하면 그 책의 미완료 기록을 반환합니다.
            없으면 204이며 응답 본문이 없습니다. reading/paused 상태만 대상으로 하며 조회 자체는 위치·상태를 바꾸지 않습니다.
            """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "최근 미완료 기록"),
            @ApiResponse(responseCode = "204", description = "미완료 기록 없음", content = @Content)})
    @GetMapping("/current")
    public ResponseEntity<ReadingResponse> current(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @Parameter(description = "선택한 책의 내부 ID. 생략 가능", example = "1")
            @RequestParam(name = "bookId", required = false) @Positive Long bookId) {
        var result = reading.current(user(principal), bookId);
        return result.map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @Operation(summary = "전체 독서 상태 조회", description = """
            시작 응답의 sessionId를 입력하세요. 현재 위치는 이어 읽기용이며 최대 위치는 감소하지 않는 진행률 계산용입니다.
            진행률은 원본 페이지 본문 길이 기준이고, 발췌 분량은 포함하지 않습니다.
            본인 소유가 아니거나 없는 기록은 404입니다. 본문 버전이 바뀌면 409이며 기존 위치를 자동 보정하지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "본인 전체 독서 상태", content = @Content(schema = @Schema(implementation = ReadingResponse.class)))
    @GetMapping("/{sessionId}")
    public ResponseEntity<ReadingResponse> get(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @PathVariable("sessionId") @Positive long sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reading.get(user(principal), sessionId));
    }

    @Operation(summary = "독서 페이지 조회", description = """
            sessionId와 1부터 시작하는 pageNo를 입력하세요. 작품 전체 범위에서 페이지 하나를 가져옵니다.
            응답은 pageNo·content입니다. content.length는 JavaScript에서 UTF-16 위치 상한으로 사용할 수 있습니다.
            페이지 조회 자체로 독서 위치가 저장되지 않습니다. progress를 별도로 호출하세요.
            """)
    @ApiResponse(responseCode = "200", description = "원본 한 페이지", content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @GetMapping("/{sessionId}/pages/{pageNo}")
    public ResponseEntity<PageResponse> page(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @PathVariable("sessionId") @Positive long sessionId, @PathVariable("pageNo") @Positive int pageNo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reading.page(user(principal), sessionId, pageNo));
    }

    @Operation(summary = "현재 위치·중단 상태 저장", description = """
            로그인·CSRF 토큰이 필요합니다. 예: {"pageNo":1,"position":10,"version":"0","status":"reading"}
            pageNo는 정수 1 이상, position은 해당 페이지의 UTF-16 위치 0~content.length입니다. 이모지 중간 위치는 거절합니다.
            status는 reading 또는 paused입니다. 현재 위치를 뒤로 옮겨도 최대 위치와 진행률은 감소하지 않습니다.
            성공 시 version이 증가합니다. 409의 current를 반영하고 과거 요청의 version만 바꿔 자동 재전송하지 마세요.
            완료한 기록은 수정할 수 없습니다. 페이지 이동·중단·완료 직전에 위치를 저장하세요.
            """, parameters = @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/csrf 실제 응답 token", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "200", description = "증가한 버전과 저장된 현재/최대 위치", content = @Content(schema = @Schema(implementation = ReadingResponse.class)))
    @PatchMapping("/{sessionId}/progress")
    public ResponseEntity<ReadingResponse> progress(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @PathVariable("sessionId") @Positive long sessionId, @Valid @RequestBody ReadingRequests.Progress input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reading.progress(user(principal), sessionId,
                input.pageNo(), input.position(), ReadingRequests.number(input.version(), false), input.status()));
    }

    @Operation(summary = "책 전체 독서 완료", description = """
            먼저 마지막 페이지 끝을 progress로 저장하세요. 저장 응답의 최신 version을 문자열로 전달합니다. 예: {"version":"1"}
            현재 위치가 마지막 끝이어야 하며 과거에 끝에 갔다가 앞부분으로 돌아온 상태는 완료할 수 없습니다.
            완료 성공 시 version이 증가합니다. 같은 최신 version으로 다시 완료하면 기존 결과를 반환합니다.
            과거 version 재전송은 409와 최신 current입니다. 100% 도달과 completed 상태는 구분합니다.
            """, parameters = @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/csrf 실제 응답 token", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "200", description = "전체 완료 기록", content = @Content(schema = @Schema(implementation = ReadingResponse.class)))
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ReadingResponse> complete(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @PathVariable("sessionId") @Positive long sessionId, @Valid @RequestBody ReadingRequests.Complete input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reading.complete(user(principal), sessionId, ReadingRequests.number(input.version(), false)));
    }

    private long user(LoginUser principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        users.getMe(principal.userId());
        return principal.userId();
    }
}
