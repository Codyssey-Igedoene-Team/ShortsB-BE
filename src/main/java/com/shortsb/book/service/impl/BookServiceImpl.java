package com.shortsb.book.service.impl;

import com.shortsb.book.dto.BookResponse;
import com.shortsb.book.dto.PageResponse;
import com.shortsb.book.repository.BookRepository;
import com.shortsb.book.repository.BookRepository.BookData;
import com.shortsb.book.repository.BookRepository.StoredPage;
import com.shortsb.book.service.BookService;
import com.shortsb.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
// 같은 DB 스냅샷의 승인 상태·본문 버전·연속 페이지 검증
public class BookServiceImpl implements BookService {

    private final BookRepository repository;

    public BookServiceImpl(BookRepository repository) {
        this.repository = repository;
    }

    @Override
    public BookResponse getBook(long bookId) {
        return approved(bookId).response();
    }

    // 전체 독서에서 한 원본 페이지 조회. 조회 자체는 진행 저장 없음
    @Override
    public PageResponse getPage(long bookId, int pageNo) {
        BookData book = approved(bookId);
        if (pageNo < 1 || pageNo > book.response().totalPageCount()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND", "해당 페이지를 찾을 수 없습니다.");
        }
        return getExcerpt(bookId, book.contentHash(), book.response().totalPageCount(), pageNo, pageNo).pages().getFirst();
    }

    @Override
    public Excerpt getExcerpt(long bookId, String contentHash, int totalPages, int startPageNo, int endPageNo) {
        BookData book = approved(bookId);
        if (!book.contentHash().equals(contentHash) || book.response().totalPageCount() != totalPages) {
            throw new ApiException(HttpStatus.CONFLICT, "PREVIEW_CONTENT_CHANGED", "본문이 변경됐습니다. 새 추천을 요청해주세요.");
        }
        if (startPageNo < 1 || endPageNo < startPageNo || endPageNo > totalPages || endPageNo - startPageNo >= 10) {
            throw unavailable();
        }
        List<StoredPage> stored = repository.findPages(bookId, startPageNo, endPageNo);
        if (stored.size() != endPageNo - startPageNo + 1) {
            throw unavailable();
        }
        List<PageResponse> pages = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            StoredPage page = stored.get(index);
            if (page.number() != startPageNo + index || page.content() == null || page.content().isBlank()
                    || page.utf16Length() != page.content().length()
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(page.content())
                    || !hash(page.content()).equals(page.contentHash())) {
                throw unavailable();
            }
            pages.add(new PageResponse(page.number(), page.content()));
        }
        return new Excerpt(book.response(), List.copyOf(pages));
    }

    private BookData approved(long bookId) {
        return repository.findApproved(bookId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "BOOK_NOT_FOUND", "제공 가능한 도서를 찾을 수 없습니다."));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONTENT_UNAVAILABLE", "제공할 본문 데이터를 확인해주세요.");
    }
}
