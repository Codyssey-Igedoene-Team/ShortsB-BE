package com.shortsb.book.controller;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.book.dto.BookResponse;
import com.shortsb.book.service.BookService;
import com.shortsb.common.dto.ApiError;
import com.shortsb.common.exception.ApiException;
import com.shortsb.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "도서", description = "제공 승인된 작품의 정보·출처·번역 안내 조회")
// 로그인한 사용자의 승인 도서 정보 조회 API
public class BookController {

    private final BookService books;
    private final UserService users;

    public BookController(BookService books, UserService users) {
        this.books = books;
        this.users = users;
    }

    @Operation(summary = "도서 정보 조회", description = """
            구글 로그인 후 발췌 응답의 book.bookId를 입력하세요. ID는 제공 자료의 문자열 ID가 아닌 서비스 내부 ID입니다.
            제목·저자·전체 쪽수·판본·출처·번역 안내를 조회하며 전체 본문을 반환하지 않습니다.
            미존재·미승인·원본 페이지가 준비되지 않은 작품은 모두 404입니다. GET 요청에는 CSRF 헤더가 필요 없습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 도서 정보"),
            @ApiResponse(responseCode = "400", description = "양의 정수가 아닌 내부 도서 ID", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요·삭제된 회원", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "BOOK_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/api/books/{bookId}")
    public ResponseEntity<BookResponse> get(@Parameter(hidden = true) @AuthenticationPrincipal LoginUser principal,
            @Parameter(description = "발췌 응답의 book.bookId", example = "1") @PathVariable("bookId") @Positive long bookId) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
        users.getMe(principal.userId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(books.getBook(bookId));
    }
}
