package com.shortsb.book.repository;

import com.shortsb.book.dto.CatalogImportData;
import com.shortsb.book.dto.CatalogImportData.Book;
import com.shortsb.book.dto.CatalogImportData.Card;
import com.shortsb.book.dto.CatalogImportData.Model;
import com.shortsb.book.dto.CatalogImportData.Page;
import com.shortsb.book.dto.CatalogImportData.Vector;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// PostgreSQL의 원본·벡터 불변 등록과 중복 데이터 대조
public class CatalogImportRepository {

    private final JdbcTemplate jdbc;

    public CatalogImportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 동일 명령의 동시 등록 직렬화. 트랜잭션 종료 시 자동 해제
    public void lockImport() {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SELECT pg_advisory_xact_lock(1936220738, 1)");
    }

    // 이미 등록한 원본은 전체 메타데이터까지 같을 때만 재사용
    public long saveBook(Book book, String version) {
        jdbc.update("""
                INSERT INTO public.books (external_id, title, author, edition, language, source_url,
                    catalog_version, page_count, text_sha256, page_joiner, source_metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (external_id) DO NOTHING
                """, book.externalId(), book.title(), book.author(), book.edition(), book.language(), book.sourceUrl(),
                version, book.pages().size(), book.textHash(), book.pageJoiner(), book.metadata().toString());
        List<Long> ids = jdbc.query("""
                SELECT id FROM public.books WHERE external_id = ? AND title = ? AND author = ?
                    AND edition = ? AND language = ? AND source_url = ? AND catalog_version = ?
                    AND page_count = ? AND text_sha256 = ? AND page_joiner = ? AND source_metadata = CAST(? AS jsonb)
                """, (rs, row) -> rs.getLong("id"), book.externalId(), book.title(), book.author(), book.edition(),
                book.language(), book.sourceUrl(), version, book.pages().size(), book.textHash(), book.pageJoiner(),
                book.metadata().toString());
        requireSame(ids.size() == 1);
        return ids.getFirst();
    }

    // 표시 페이지와 현재의 페이지 전체 검색 구간을 한 쌍으로 저장
    public long savePage(long bookId, Page page) {
        jdbc.update("""
                INSERT INTO public.book_pages (book_id, page_no, content, content_sha256, utf16_length)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (book_id, page_no) DO NOTHING
                """, bookId, page.number(), page.content(), page.contentHash(), page.content().length());
        requireSame(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.book_pages WHERE book_id = ? AND page_no = ?
                    AND content = ? AND content_sha256 = ? AND utf16_length = ?)
                """, Boolean.class, bookId, page.number(), page.content(), page.contentHash(), page.content().length())));
        jdbc.update("""
                INSERT INTO public.book_search_segments
                    (external_id, book_id, page_no, start_position, end_position, content_sha256)
                VALUES (?, ?, ?, 0, ?, ?) ON CONFLICT (external_id) DO NOTHING
                """, page.segmentId(), bookId, page.number(), page.content().length(), page.contentHash());
        List<Long> ids = jdbc.query("""
                SELECT id FROM public.book_search_segments WHERE external_id = ? AND book_id = ?
                    AND page_no = ? AND start_position = 0 AND end_position = ? AND content_sha256 = ?
                """, (rs, row) -> rs.getLong("id"), page.segmentId(), bookId, page.number(),
                page.content().length(), page.contentHash());
        requireSame(ids.size() == 1);
        return ids.getFirst();
    }

    // 기존의 초과 페이지·검색 구간 거절과 무단 삭제 방지
    public void verifyBookCounts(long bookId, int pageCount) {
        requireSame(jdbc.queryForObject("SELECT count(*) FROM public.book_pages WHERE book_id = ?", Long.class, bookId) == pageCount
                && jdbc.queryForObject("SELECT count(*) FROM public.book_search_segments WHERE book_id = ?", Long.class, bookId) == pageCount);
    }

    public void saveModel(Model model) {
        jdbc.update("""
                INSERT INTO public.embedding_models (fingerprint, model_id, revision, dimension, normalized, configuration)
                VALUES (?, ?, ?, ?, true, CAST(? AS jsonb)) ON CONFLICT (fingerprint) DO NOTHING
                """, model.fingerprint(), model.modelId(), model.revision(), model.dimension(), model.configuration().toString());
        requireSame(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.embedding_models WHERE fingerprint = ? AND model_id = ?
                    AND revision = ? AND dimension = ? AND normalized = true AND configuration = CAST(? AS jsonb))
                """, Boolean.class, model.fingerprint(), model.modelId(), model.revision(), model.dimension(), model.configuration().toString())));
    }

    public void saveDocument(long segmentId, Model model, Vector vector) {
        String values = array(vector.values());
        jdbc.update("""
                INSERT INTO public.book_segment_embeddings (segment_id, model_fingerprint, dimension, embedding)
                VALUES (?, ?, ?, CAST(? AS double precision[])) ON CONFLICT (segment_id, model_fingerprint) DO NOTHING
                """, segmentId, model.fingerprint(), model.dimension(), values);
        requireSame(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.book_segment_embeddings WHERE segment_id = ?
                    AND model_fingerprint = ? AND dimension = ? AND embedding = CAST(? AS double precision[]))
                """, Boolean.class, segmentId, model.fingerprint(), model.dimension(), values)));
    }

    public void saveCard(Model model, Card card) {
        String values = array(card.values());
        jdbc.update("""
                INSERT INTO public.card_embeddings
                    (model_fingerprint, card_code, input_id, query_text, content_sha256, dimension, embedding)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS double precision[]))
                ON CONFLICT (model_fingerprint, card_code) DO NOTHING
                """, model.fingerprint(), card.code(), card.inputId(), card.text(), card.contentHash(), model.dimension(), values);
        requireSame(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.card_embeddings WHERE model_fingerprint = ? AND card_code = ?
                    AND input_id = ? AND query_text = ? AND content_sha256 = ? AND dimension = ?
                    AND embedding = CAST(? AS double precision[]))
                """, Boolean.class, model.fingerprint(), card.code(), card.inputId(), card.text(), card.contentHash(),
                model.dimension(), values)));
    }

    // 전체 등록 성공 시에만 사용할 모델과 원본 자료 정보를 원자적으로 전환
    public void activate(CatalogImportData data) {
        jdbc.update("""
                INSERT INTO public.recommendation_configuration
                    (id, model_fingerprint, catalog_version, catalog_source_sha256, book_count, segment_count)
                VALUES (1, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET model_fingerprint = EXCLUDED.model_fingerprint,
                    catalog_version = EXCLUDED.catalog_version, catalog_source_sha256 = EXCLUDED.catalog_source_sha256,
                    book_count = EXCLUDED.book_count, segment_count = EXCLUDED.segment_count, updated_at = now()
                """, data.model().fingerprint(), data.version(), data.sourceHash(), data.books().size(), data.documents().size());
    }

    // 검증된 유한 실수만 바인딩 매개변수로 전달. SQL 문자열에 값 삽입 금지
    private String array(List<Double> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private void requireSame(boolean same) {
        if (!same) {
            throw new IllegalStateException("기존 도서·페이지·모델·벡터가 입력과 다릅니다. 기존 데이터를 덮어쓰지 않습니다.");
        }
    }
}
