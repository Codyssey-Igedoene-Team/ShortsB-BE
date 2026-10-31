package com.shortsb.recommendation.service.impl;

import com.shortsb.recommendation.dto.ContrastReview;
import com.shortsb.recommendation.dto.ContrastReview.Evidence;
import com.shortsb.recommendation.repository.ContrastRepository;
import com.shortsb.recommendation.repository.ContrastRepository.ReviewBook;
import com.shortsb.recommendation.service.ContrastEvidenceValidator;
import com.shortsb.recommendation.service.ContrastImportService;
import com.shortsb.recommendation.service.ContrastReviewReader;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 본문 출력 없는 검수 자료 등록. 한 항목 실패 시 전체 롤백
public class ContrastImportServiceImpl implements ContrastImportService {

    private final ContrastReviewReader reader;
    private final ContrastRepository repository;
    private final ContrastEvidenceValidator evidence;

    public ContrastImportServiceImpl(ContrastReviewReader reader, ContrastRepository repository, ContrastEvidenceValidator evidence) {
        this.reader = reader;
        this.repository = repository;
        this.evidence = evidence;
    }

    @Override
    @Transactional
    public int importReviews(Path input, boolean apply) {
        var rows = reader.read(input);
        if (apply) {
            repository.lockImport();
        }
        for (ContrastReview row : rows) {
            ReviewBook source = repository.findBook(row.sourceBookId()).orElseThrow(this::invalid);
            ReviewBook target = repository.findBook(row.targetBookId()).orElseThrow(this::invalid);
            require(row.sourceContentHash() == null || row.sourceContentHash().equals(source.hash()));
            require(row.targetContentHash() == null || row.targetContentHash().equals(target.hash()));
            range(source, row.sourceEvidence());
            range(target, row.targetEvidence());
            if (row.reviewStatus().equals("approved")) {
                require(source.rights().equals("approved") && target.rights().equals("approved"));
                require(evidence.matches(source.id(), row.sourceContentHash(), source.totalPages(), source.joiner(), row.sourceEvidence())
                        && evidence.matches(target.id(), row.targetContentHash(), target.totalPages(), target.joiner(), row.targetEvidence()));
            }
            if (apply) {
                repository.save(row, source.id(), target.id());
            }
        }
        return rows.size();
    }

    private void range(ReviewBook book, Evidence value) {
        require(value == null || value.endPageNo() <= book.totalPages());
    }

    private void require(boolean valid) {
        if (!valid) {
            throw invalid();
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("양쪽 도서·권한·본문 해시·근거 범위·정확한 구절을 확인해주세요.");
    }
}
