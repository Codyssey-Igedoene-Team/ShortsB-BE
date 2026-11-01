package com.shortsb.recommendation.service.impl;

import com.shortsb.book.repository.BookRepository;
import com.shortsb.book.repository.BookRepository.BookData;
import com.shortsb.book.service.BookService;
import com.shortsb.common.exception.ApiException;
import com.shortsb.embedding.service.EmbeddingService;
import com.shortsb.recommendation.config.RecommendationProperties;
import com.shortsb.recommendation.dto.PreviewRequest;
import com.shortsb.recommendation.dto.PreviewResponse;
import com.shortsb.recommendation.dto.PreviewState;
import com.shortsb.recommendation.dto.StoryType;
import com.shortsb.recommendation.repository.RecommendationRepository;
import com.shortsb.recommendation.repository.RecommendationRepository.Candidate;
import com.shortsb.recommendation.repository.RecommendationRepository.SearchData;
import com.shortsb.recommendation.service.PreviewService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static com.shortsb.recommendation.service.VectorSimilarity.cosine;

@Service
// 입력별 추천 경로·유사도 점수·연속 발췌 범위 계산
public class PreviewServiceImpl implements PreviewService {

    private final BookRepository books;
    private final RecommendationRepository recommendations;
    private final BookService bookService;
    private final EmbeddingService embeddings;
    private final RecommendationProperties properties;

    public PreviewServiceImpl(BookRepository books, RecommendationRepository recommendations,
            BookService bookService, EmbeddingService embeddings, RecommendationProperties properties) {
        this.books = books;
        this.recommendations = recommendations;
        this.bookService = bookService;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    @Override
    public CreatedPreview create(long userId, PreviewRequest request) {
        String prompt = request.normalizedPrompt();
        if (request.prompt() != null && (!StandardCharsets.UTF_8.newEncoder().canEncode(request.prompt())
                || request.prompt().indexOf('\0') >= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "프롬프트의 문자 인코딩을 확인해주세요.");
        }
        Selection selection;
        if (request.storyType() == StoryType.ANY && prompt == null) {
            List<BookData> candidates = books.findApprovedBooks();
            if (candidates.isEmpty()) {
                throw RecommendationRepository.noCandidates();
            }
            // 작품별 한 항목에서 선택. 긴 책의 구간 수가 선택 확률에 영향 없음
            selection = new Selection(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())),
                    1, null, "RANDOM", null, "분위기를 정하지 않아 여러 작품 중 한 권을 골랐어요.");
        } else {
            selection = search(request.storyType(), prompt);
        }
        int total = selection.book().response().totalPageCount();
        int actual = Math.min(request.requestedPageCount(), total);
        int start = Math.max(1, Math.min(selection.pageNo() - (actual - 1) / 2, total - actual + 1));
        PreviewState state = new PreviewState(UUID.randomUUID(), userId, selection.book().id(), selection.book().contentHash(),
                total, request.requestedPageCount(), start, start + actual - 1, request.storyType(), prompt,
                selection.modelFingerprint(), selection.type(), selection.matchedPageNo(), selection.reason());
        // 최신 승인 상태·본문 해시·페이지를 확인한 후에만 세션 보관 대상으로 반환
        return new CreatedPreview(get(state), state);
    }

    @Override
    public PreviewResponse get(PreviewState state) {
        BookService.Excerpt excerpt = bookService.getExcerpt(state.bookId(), state.contentHash(), state.totalPages(),
                state.startPageNo(), state.endPageNo());
        return new PreviewResponse(state.previewId().toString(), excerpt.book(), state.requestedPageCount(),
                excerpt.pages().size(), state.startPageNo(), state.endPageNo(), state.storyType(),
                state.recommendationType(), state.matchedPageNo(), state.reason(), excerpt.pages());
    }

    private Selection search(StoryType type, String prompt) {
        SearchData data = recommendations.loadSearchData(type);
        // 프롬프트가 있는 경우에만 온라인 GPU 요청. 조회 트랜잭션은 이미 종료
        List<Double> query = prompt == null ? List.of() : embeddings.embedQuery(prompt, data.model());
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        // ponytail: 초기 864개 벡터의 전수 검색. 자료 확대·지연 측정 후 DB 벡터 인덱스로 전환
        for (Candidate candidate : data.candidates()) {
            double score;
            if (type == StoryType.ANY) {
                score = cosine(query, candidate.values());
            } else if (prompt == null) {
                score = cosine(data.card(), candidate.values());
            } else {
                score = (1.0 - properties.promptWeight()) * cosine(data.card(), candidate.values())
                        + properties.promptWeight() * cosine(query, candidate.values());
            }
            // 동점은 저장 조회 순서인 도서 ID·페이지·구간 ID 오름차순 유지
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) {
            throw RecommendationRepository.notReady();
        }
        String path = type == StoryType.ANY ? "PROMPT" : prompt == null ? "CARD" : "CARD_AND_PROMPT";
        String reason = switch (path) {
            case "CARD" -> "선택한 이야기 분위기와 가까운 대목을 골랐어요.";
            case "PROMPT" -> "입력한 요청과 의미가 가까운 대목을 골랐어요.";
            default -> "선택한 이야기 분위기와 입력한 요청을 함께 반영해 대목을 골랐어요.";
        };
        return new Selection(data.books().get(best.bookId()), best.pageNo(), data.model().fingerprint(),
                path, best.pageNo(), reason);
    }

    private record Selection(BookData book, int pageNo, String modelFingerprint, String type,
            Integer matchedPageNo, String reason) {}
}
