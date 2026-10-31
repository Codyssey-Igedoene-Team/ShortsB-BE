package com.shortsb.recommendation.repository;

import com.shortsb.book.repository.BookRepository;
import com.shortsb.book.repository.BookRepository.BookData;
import com.shortsb.common.exception.ApiException;
import com.shortsb.embedding.dto.EmbeddingModel;
import com.shortsb.recommendation.dto.StoryType;
import java.lang.reflect.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
// 동일한 DB 스냅샷에서 활성 모델·승인 작품·벡터 준비 상태 조회
public class RecommendationRepository {

    private final JdbcTemplate jdbc;
    private final BookRepository books;

    public RecommendationRepository(JdbcTemplate jdbc, BookRepository books) {
        this.jdbc = jdbc;
        this.books = books;
    }

    // GPU 요청 전에 조회 트랜잭션 종료. 본문은 검색 스냅샷에 포함하지 않음
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchData loadSearchData(StoryType type) {
        List<ActiveModel> active = jdbc.query("""
                SELECT m.model_id, m.revision, m.fingerprint, m.dimension, m.normalized, c.catalog_version
                FROM public.recommendation_configuration c
                JOIN public.embedding_models m ON m.fingerprint = c.model_fingerprint WHERE c.id = 1
                """, (rs, row) -> {
                    try {
                        return new ActiveModel(new EmbeddingModel(rs.getString("model_id"), rs.getString("revision"),
                                rs.getString("fingerprint"), rs.getInt("dimension"), rs.getBoolean("normalized")),
                                rs.getString("catalog_version"));
                    } catch (IllegalArgumentException exception) {
                        throw notReady();
                    }
                });
        if (active.size() != 1) {
            throw notReady();
        }
        ActiveModel selected = active.getFirst();
        EmbeddingModel model = selected.model();
        List<BookData> approved = books.findApprovedBooks().stream()
                .filter(book -> selected.catalogVersion().equals(book.catalogVersion())).toList();
        if (approved.isEmpty()) {
            throw noCandidates();
        }
        List<Candidate> candidates = jdbc.query("""
                SELECT s.id, s.book_id, s.page_no, e.embedding FROM public.book_segment_embeddings e
                JOIN public.book_search_segments s ON s.id = e.segment_id
                JOIN public.book_pages p ON p.book_id = s.book_id AND p.page_no = s.page_no
                JOIN public.books b ON b.id = s.book_id
                WHERE e.model_fingerprint = ? AND e.dimension = ? AND b.content_rights = 'approved'
                    AND b.catalog_version = ? AND s.page_no BETWEEN 1 AND b.page_count
                    AND s.start_position = 0 AND s.end_position = p.utf16_length
                    AND s.content_sha256 = p.content_sha256
                ORDER BY s.book_id, s.page_no, s.id
                """, (rs, row) -> new Candidate(rs.getLong("id"), rs.getLong("book_id"), rs.getInt("page_no"),
                        vector(rs, model.dimension())), model.fingerprint(), model.dimension(), selected.catalogVersion());
        Map<Long, Set<Integer>> coverage = new HashMap<>();
        for (Candidate candidate : candidates) {
            coverage.computeIfAbsent(candidate.bookId(), key -> new HashSet<>()).add(candidate.pageNo());
        }
        Map<Long, BookData> readyBooks = new HashMap<>();
        for (BookData book : approved) {
            if (coverage.getOrDefault(book.id(), Set.of()).size() == book.response().totalPageCount()) {
                readyBooks.put(book.id(), book);
            }
        }
        if (readyBooks.isEmpty()) {
            throw notReady();
        }
        List<Double> card = List.of();
        if (type != StoryType.ANY) {
            List<List<Double>> cards = jdbc.query("""
                    SELECT embedding FROM public.card_embeddings WHERE model_fingerprint = ?
                        AND card_code = ? AND dimension = ?
                    """, (rs, row) -> vector(rs, model.dimension()), model.fingerprint(), type.name(), model.dimension());
            if (cards.size() != 1) {
                throw notReady();
            }
            card = cards.getFirst();
        }
        return new SearchData(model, Map.copyOf(readyBooks),
                candidates.stream().filter(candidate -> readyBooks.containsKey(candidate.bookId())).toList(), card);
    }

    // JDBC 원시/객체 배열을 동일하게 변환하고 유한값·차원·정규화 검사
    private List<Double> vector(ResultSet result, int dimension) throws SQLException {
        java.sql.Array sqlArray = result.getArray("embedding");
        if (sqlArray == null) {
            throw notReady();
        }
        try {
            Object values = sqlArray.getArray();
            if (values == null || !values.getClass().isArray() || Array.getLength(values) != dimension) {
                throw notReady();
            }
            List<Double> vector = new ArrayList<>(dimension);
            double sum = 0;
            for (int index = 0; index < dimension; index++) {
                Object value = Array.get(values, index);
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    throw notReady();
                }
                double scalar = number.doubleValue();
                vector.add(scalar);
                sum += scalar * scalar;
            }
            if (!Double.isFinite(sum) || Math.abs(Math.sqrt(sum) - 1.0) > 1e-3) {
                throw notReady();
            }
            return List.copyOf(vector);
        } finally {
            sqlArray.free();
        }
    }

    public static ApiException noCandidates() {
        return new ApiException(HttpStatus.NOT_FOUND, "NO_RECOMMENDATION_CANDIDATES", "현재 제공 가능한 추천 작품이 없습니다.");
    }

    public static ApiException notReady() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RECOMMENDATION_NOT_READY", "추천 모델·카드·본문 벡터를 확인해주세요.");
    }

    private record ActiveModel(EmbeddingModel model, String catalogVersion) {}

    public record Candidate(long segmentId, long bookId, int pageNo, List<Double> values) {}

    public record SearchData(EmbeddingModel model, Map<Long, BookData> books, List<Candidate> candidates, List<Double> card) {}
}
