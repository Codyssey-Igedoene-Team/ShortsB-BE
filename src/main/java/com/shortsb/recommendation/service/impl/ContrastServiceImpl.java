package com.shortsb.recommendation.service.impl;

import com.shortsb.book.repository.BookRepository;
import com.shortsb.book.repository.BookRepository.BookData;
import com.shortsb.book.service.BookService;
import com.shortsb.common.exception.ApiException;
import com.shortsb.recommendation.dto.ContrastResponse;
import com.shortsb.recommendation.dto.ContrastResponse.EvidenceReference;
import com.shortsb.recommendation.dto.ContrastReview.Evidence;
import com.shortsb.recommendation.dto.PreviewState;
import com.shortsb.recommendation.repository.ContrastRepository;
import com.shortsb.recommendation.repository.ContrastRepository.Relation;
import com.shortsb.recommendation.repository.RecommendationRepository;
import com.shortsb.recommendation.repository.RecommendationRepository.SearchData;
import com.shortsb.recommendation.service.ContrastEvidenceValidator;
import com.shortsb.recommendation.service.ContrastService;
import com.shortsb.recommendation.service.VectorSimilarity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
// 양쪽 승인·본문 버전·근거 포함을 유지하는 대비 발췌 검색
public class ContrastServiceImpl implements ContrastService {

    private final BookRepository books;
    private final BookService content;
    private final ContrastRepository relations;
    private final RecommendationRepository recommendations;
    private final ContrastEvidenceValidator evidence;

    public ContrastServiceImpl(BookRepository books, BookService content, ContrastRepository relations,
            RecommendationRepository recommendations, ContrastEvidenceValidator evidence) {
        this.books = books;
        this.content = content;
        this.relations = relations;
        this.recommendations = recommendations;
        this.evidence = evidence;
    }

    @Override
    public ContrastResponse recommend(PreviewState source) {
        // 원래 첫 발췌의 승인·해시·실제 페이지 무결성 확인
        content.getExcerpt(source.bookId(), source.contentHash(), source.totalPages(), source.startPageNo(), source.endPageNo());
        Map<Long, BookData> approved = new HashMap<>();
        for (BookData book : books.findApprovedBooks()) {
            approved.put(book.id(), book);
        }
        List<Relation> eligible = relations.approved(source.bookId(), source.startPageNo(), source.endPageNo()).stream()
                .filter(relation -> approved.containsKey(relation.targetId()))
                .filter(relation -> fits(relation.target(), source.requestedPageCount(), approved.get(relation.targetId()).response().totalPageCount()))
                .toList();
        if (eligible.isEmpty()) {
            return new ContrastResponse(false, source.previewId().toString(), "NO_ELIGIBLE_CONTRAST", null);
        }
        BookData sourceBook = approved.get(source.bookId());
        if (sourceBook == null) {
            throw RecommendationRepository.noCandidates();
        }
        SearchData data = recommendations.loadContrastData(source.modelFingerprint(), sourceBook.catalogVersion());
        Map<Long, Map<Integer, List<Double>>> vectors = new HashMap<>();
        for (var candidate : data.candidates()) {
            vectors.computeIfAbsent(candidate.bookId(), key -> new HashMap<>()).put(candidate.pageNo(), candidate.values());
        }
        List<Double> sourceVector = window(vectors, source.bookId(), source.startPageNo(), source.endPageNo());
        Choice best = null;
        // ponytail: 초기 CSV 45개 후보와 각 최대 10개 연속 창 검색. 자료 확대 시 검색 인덱스 검토
        for (Relation relation : eligible) {
            BookData target = data.books().get(relation.targetId());
            if (target == null) {
                continue;
            }
            int count = Math.min(source.requestedPageCount(), target.response().totalPageCount());
            int first = Math.max(1, relation.target().endPageNo() - count + 1);
            int last = Math.min(relation.target().startPageNo(), target.response().totalPageCount() - count + 1);
            for (int index = 0; index <= last - first; index++) {
                int start = first + index;
                double score = VectorSimilarity.cosine(sourceVector, window(vectors, target.id(), start, start + count - 1));
                if (best == null || score > best.score()) {
                    best = new Choice(relation, target, start, start + count - 1, score);
                }
            }
        }
        if (best == null) {
            throw RecommendationRepository.notReady();
        }
        Relation relation = best.relation();
        // 승인 구절도 실제 원본에 포함되는지 다시 확인. 불일치를 후보 없음으로 숨기지 않음
        if (!evidence.matches(source.bookId(), relation.sourceHash(), source.totalPages(), relations.joiner(source.bookId()), relation.source())
                || !evidence.matches(best.book().id(), relation.targetHash(), best.book().response().totalPageCount(),
                        relations.joiner(best.book().id()), relation.target())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONTRAST_EVIDENCE_INVALID", "승인된 대비 근거와 원본 범위를 확인해주세요.");
        }
        var excerpt = content.getExcerpt(best.book().id(), relation.targetHash(), best.book().response().totalPageCount(), best.start(), best.end());
        var result = new ContrastResponse.Result(Long.toString(relation.id()), relation.axis(), relation.axis().label(),
                relation.topic(), relation.reason(), source.requestedPageCount(), excerpt.pages().size(), excerpt.book(),
                best.start(), best.end(), reference(source.bookId(), relation.source()), reference(best.book().id(), relation.target()), excerpt.pages());
        return new ContrastResponse(true, source.previewId().toString(), null, result);
    }

    private boolean fits(Evidence range, int requested, int total) {
        return range != null && range.startPageNo() >= 1 && range.endPageNo() <= total
                && range.endPageNo() >= range.startPageNo()
                && range.endPageNo() - range.startPageNo() + 1 <= Math.min(requested, total);
    }

    private List<Double> window(Map<Long, Map<Integer, List<Double>>> vectors, long bookId, int start, int end) {
        Map<Integer, List<Double>> pages = vectors.get(bookId);
        if (pages == null) {
            throw RecommendationRepository.notReady();
        }
        List<List<Double>> values = new ArrayList<>();
        for (int index = 0; index <= end - start; index++) {
            List<Double> value = pages.get(start + index);
            if (value == null) {
                throw RecommendationRepository.notReady();
            }
            values.add(value);
        }
        return VectorSimilarity.mean(values);
    }

    private EvidenceReference reference(long bookId, Evidence value) {
        return new EvidenceReference(Long.toString(bookId), value.startPageNo(), value.endPageNo(), value.quote());
    }

    private record Choice(Relation relation, BookData book, int start, int end, double score) {}
}
