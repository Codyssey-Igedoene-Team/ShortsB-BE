package com.shortsb.book.service;

import com.shortsb.book.dto.BookResponse;
import com.shortsb.book.dto.PageResponse;
import java.util.List;

// 승인 도서 조회와 저장 발췌 범위 복원 계약
public interface BookService {

    BookResponse getBook(long bookId);

    Excerpt getExcerpt(long bookId, String contentHash, int totalPages, int startPageNo, int endPageNo);

    record Excerpt(BookResponse book, List<PageResponse> pages) {}
}
