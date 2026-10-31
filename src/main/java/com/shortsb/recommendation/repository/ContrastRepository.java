package com.shortsb.recommendation.repository;

import com.shortsb.recommendation.dto.ContrastAxis;
import com.shortsb.recommendation.dto.ContrastReview;
import com.shortsb.recommendation.dto.ContrastReview.Evidence;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// 대비 후보·명시적 검수 결과 저장과 승인된 방향성 관계 조회
public class ContrastRepository {

    private final JdbcTemplate jdbc;

    public ContrastRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReviewBook> findBook(String externalId) {
        return jdbc.query("""
                SELECT id, text_sha256, page_count, page_joiner, content_rights FROM public.books
                WHERE external_id = ?
                """, (rs, row) -> new ReviewBook(rs.getLong("id"), rs.getString("text_sha256"), rs.getInt("page_count"),
                        rs.getString("page_joiner"), rs.getString("content_rights")), externalId).stream().findFirst();
    }

    public String joiner(long bookId) {
        return jdbc.queryForObject("SELECT page_joiner FROM public.books WHERE id = ?", String.class, bookId);
    }

    public void lockImport() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SELECT pg_advisory_xact_lock(1936220738, 2)");
        // 근거 대조와 승인 반영 사이의 도서·페이지 수정 방지
        jdbc.execute("LOCK TABLE public.books, public.book_pages IN SHARE MODE");
    }

    public void save(ContrastReview review, long source, long target) {
        Evidence left = review.sourceEvidence();
        Evidence right = review.targetEvidence();
        String conflict = review.reviewStatus().equals("pending") ? "DO NOTHING" : """
                DO UPDATE SET common_topic = EXCLUDED.common_topic, reason = EXCLUDED.reason,
                    source_content_sha256 = EXCLUDED.source_content_sha256, target_content_sha256 = EXCLUDED.target_content_sha256,
                    source_start_page_no = EXCLUDED.source_start_page_no, source_end_page_no = EXCLUDED.source_end_page_no,
                    source_evidence = EXCLUDED.source_evidence, target_start_page_no = EXCLUDED.target_start_page_no,
                    target_end_page_no = EXCLUDED.target_end_page_no, target_evidence = EXCLUDED.target_evidence,
                    review_status = EXCLUDED.review_status, reviewed_by = EXCLUDED.reviewed_by,
                    reviewed_at = EXCLUDED.reviewed_at, updated_at = statement_timestamp()
                """;
        jdbc.update("""
                INSERT INTO public.book_contrast_relations
                    (source_book_id, target_book_id, axis, common_topic, reason, source_content_sha256, target_content_sha256,
                    source_start_page_no, source_end_page_no, source_evidence, target_start_page_no, target_end_page_no,
                    target_evidence, review_status, reviewed_by, reviewed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    CASE WHEN ? = 'pending' THEN NULL ELSE statement_timestamp() END)
                ON CONFLICT (source_book_id, target_book_id, axis)
                """ + conflict, source, target, review.axis().code(), review.commonTopic(), review.reason(),
                review.sourceContentHash(), review.targetContentHash(), left == null ? null : left.startPageNo(),
                left == null ? null : left.endPageNo(), left == null ? null : left.quote(),
                right == null ? null : right.startPageNo(), right == null ? null : right.endPageNo(),
                right == null ? null : right.quote(), review.reviewStatus(), review.reviewedBy(), review.reviewStatus());
    }

    // 기준 근거 전체가 첫 실제 발췌에 포함되고 양쪽 본문 버전·권한이 일치하는 관계만 조회
    public List<Relation> approved(long sourceId, int start, int end) {
        return jdbc.query("""
                SELECT r.* FROM public.book_contrast_relations r
                JOIN public.books s ON s.id = r.source_book_id JOIN public.books t ON t.id = r.target_book_id
                WHERE r.source_book_id = ? AND r.review_status = 'approved'
                    AND s.content_rights = 'approved' AND t.content_rights = 'approved'
                    AND r.source_content_sha256 = s.text_sha256 AND r.target_content_sha256 = t.text_sha256
                    AND r.source_start_page_no >= ? AND r.source_end_page_no <= ? ORDER BY r.id
                """, (rs, row) -> new Relation(rs.getLong("id"), rs.getLong("source_book_id"), rs.getLong("target_book_id"),
                        ContrastAxis.fromCode(rs.getString("axis")), rs.getString("common_topic"), rs.getString("reason"),
                        rs.getString("source_content_sha256"), rs.getString("target_content_sha256"),
                        new Evidence(rs.getInt("source_start_page_no"), rs.getInt("source_end_page_no"), rs.getString("source_evidence")),
                        new Evidence(rs.getInt("target_start_page_no"), rs.getInt("target_end_page_no"), rs.getString("target_evidence"))), sourceId, start, end);
    }

    public record ReviewBook(long id, String hash, int totalPages, String joiner, String rights) {}

    public record Relation(long id, long sourceId, long targetId, ContrastAxis axis, String topic, String reason,
            String sourceHash, String targetHash, Evidence source, Evidence target) {
        @Override
        public String toString() {
            return "Relation[id=" + id + ", axis=" + axis + "]";
        }
    }
}
