package com.shortsb.recommendation.service;

import com.shortsb.book.service.BookService;
import com.shortsb.recommendation.dto.ContrastReview.Evidence;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
// 프로그램의 정확한 구절 포함 검사. 의미적 대비 승인은 사람이 수행
public class ContrastEvidenceValidator {

    private final BookService books;

    public ContrastEvidenceValidator(BookService books) {
        this.books = books;
    }

    public boolean matches(long bookId, String hash, int totalPages, String joiner, Evidence evidence) {
        if (evidence == null || evidence.startPageNo() < 1 || evidence.endPageNo() < evidence.startPageNo()
                || evidence.endPageNo() > totalPages || evidence.endPageNo() - evidence.startPageNo() >= 10
                || evidence.quote() == null || evidence.quote().isBlank() || joiner == null) {
            return false;
        }
        var excerpt = books.getExcerpt(bookId, hash, totalPages, evidence.startPageNo(), evidence.endPageNo());
        String text = excerpt.pages().stream().map(page -> page.content()).collect(Collectors.joining(joiner));
        return text.contains(evidence.quote());
    }
}
