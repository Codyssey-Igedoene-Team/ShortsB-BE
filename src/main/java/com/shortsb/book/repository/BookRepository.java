package com.shortsb.book.repository;

import com.shortsb.book.dto.BookResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
// 승인 도서 메타데이터·표시 페이지 조회
public class BookRepository {

    private static final String BOOKS = """
            SELECT b.id, b.external_id, b.catalog_version, b.text_sha256, b.title, b.author,
                b.page_count, b.edition, b.language, b.source_url,
                b.source_metadata ->> 'editionLabel' AS edition_label,
                b.source_metadata ->> 'workType' AS work_type,
                b.source_metadata ->> 'sourceName' AS source_name,
                b.source_metadata ->> 'licenseURL' AS license_url,
                b.source_metadata ->> 'licenseLabel' AS license_label,
                b.source_metadata ->> 'credits' AS credits,
                b.source_metadata ->> 'translationNote' AS translation_note
            FROM public.books b
            WHERE b.content_rights = 'approved' AND b.external_id IS NOT NULL AND b.page_count > 0
                AND (SELECT count(*) FROM public.book_pages p WHERE p.book_id = b.id) = b.page_count
                AND (SELECT min(p.page_no) FROM public.book_pages p WHERE p.book_id = b.id) = 1
                AND (SELECT max(p.page_no) FROM public.book_pages p WHERE p.book_id = b.id) = b.page_count
            """;
    private static final RowMapper<BookData> BOOK_ROW = (rs, row) -> new BookData(rs.getLong("id"),
            rs.getString("external_id"), rs.getString("catalog_version"), rs.getString("text_sha256"),
            new BookResponse(rs.getString("id"), rs.getString("title"), rs.getString("author"),
                    rs.getInt("page_count"), rs.getString("edition"), rs.getString("edition_label"),
                    rs.getString("language"), rs.getString("work_type"), rs.getString("source_name"),
                    rs.getString("source_url"), rs.getString("license_url"), rs.getString("license_label"),
                    rs.getString("credits"), rs.getString("translation_note")));

    private final JdbcTemplate jdbc;

    public BookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 내부 ID의 미존재·미승인 자료는 동일한 조회 실패
    public Optional<BookData> findApproved(long bookId) {
        return jdbc.query(BOOKS + " AND b.id = ?", BOOK_ROW, bookId).stream().findFirst();
    }

    // 페이지 수와 무관하게 작품 하나를 목록 한 항목으로 반환
    public List<BookData> findApprovedBooks() {
        return jdbc.query(BOOKS + " ORDER BY b.id", BOOK_ROW);
    }

    public List<StoredPage> findPages(long bookId, int startPageNo, int endPageNo) {
        return jdbc.query("""
                SELECT page_no, content, content_sha256, utf16_length FROM public.book_pages
                WHERE book_id = ? AND page_no BETWEEN ? AND ? ORDER BY page_no
                """, (rs, row) -> new StoredPage(rs.getInt("page_no"), rs.getString("content"),
                        rs.getString("content_sha256"), rs.getInt("utf16_length")), bookId, startPageNo, endPageNo);
    }

    // 원본 버전·본문 해시를 포함한 내부 조회 결과
    public record BookData(long id, String externalId, String catalogVersion, String contentHash, BookResponse response) {}

    // 로그 출력에서 본문 제외
    public record StoredPage(int number, String content, String contentHash, int utf16Length) {
        @Override
        public String toString() {
            return "StoredPage[number=" + number + "]";
        }
    }
}
