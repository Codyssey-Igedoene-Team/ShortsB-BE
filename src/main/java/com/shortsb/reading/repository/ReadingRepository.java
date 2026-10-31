package com.shortsb.reading.repository;

import com.shortsb.common.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
// 전체 독서 기록과 원본 페이지 길이 조회·조건부 저장
public class ReadingRepository {

    private static final RowMapper<State> ROW = (rs, row) -> new State(rs.getLong("id"), rs.getLong("book_id"),
            rs.getString("content_sha256"), rs.getInt("total_pages"), rs.getLong("total_utf16_length"),
            rs.getInt("current_page_no"), rs.getInt("current_position"), rs.getInt("furthest_page_no"),
            rs.getInt("furthest_position"), rs.getLong("furthest_offset"), rs.getString("status"), rs.getLong("version"),
            rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());

    private final JdbcTemplate jdbc;

    public ReadingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ponytail: 동일 회원의 쓰기 직렬화. 다수 기기의 병렬 저장이 병목일 때 회원/책 단위로 세분화
    public void lockUser(long userId) {
        if (jdbc.query("SELECT id FROM public.users WHERE id = ? FOR UPDATE", (rs, row) -> rs.getLong(1), userId).isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
    }

    // 쓰기 중 도서 승인·본문 버전 변경 방지. 페이지 본문은 가져오지 않음
    public void lockBook(long bookId) {
        jdbc.query("SELECT id FROM public.books WHERE id = ? FOR SHARE", (rs, row) -> rs.getLong(1), bookId);
    }

    public Optional<State> find(long userId, long sessionId, boolean lock) {
        return jdbc.query("SELECT * FROM public.book_reading_sessions WHERE user_id = ? AND id = ?"
                + (lock ? " FOR UPDATE" : ""), ROW, userId, sessionId).stream().findFirst();
    }

    public Optional<State> current(long userId, Long bookId) {
        String sql = "SELECT * FROM public.book_reading_sessions WHERE user_id = ? AND status IN ('reading', 'paused')";
        if (bookId == null) {
            return jdbc.query(sql + " ORDER BY updated_at DESC, id DESC LIMIT 1", ROW, userId).stream().findFirst();
        }
        return jdbc.query(sql + " AND book_id = ? ORDER BY updated_at DESC, id DESC LIMIT 1", ROW, userId, bookId).stream().findFirst();
    }

    public long insert(long userId, long bookId, String hash, int pages, long totalLength) {
        return jdbc.queryForObject("""
                INSERT INTO public.book_reading_sessions
                    (user_id, book_id, content_sha256, total_pages, total_utf16_length, started_at, updated_at)
                VALUES (?, ?, ?, ?, ?, statement_timestamp(), statement_timestamp()) RETURNING id
                """, Long.class, userId, bookId, hash, pages, totalLength);
    }

    public List<PageLength> pageLengths(long bookId) {
        return jdbc.query("SELECT page_no, utf16_length FROM public.book_pages WHERE book_id = ? ORDER BY page_no",
                (rs, row) -> new PageLength(rs.getInt("page_no"), rs.getInt("utf16_length")), bookId);
    }

    public int save(long userId, State state, int page, int position, int furthestPage, int furthestPosition,
            long offset, String status) {
        return jdbc.update("""
                UPDATE public.book_reading_sessions SET current_page_no = ?, current_position = ?,
                    furthest_page_no = ?, furthest_position = ?, furthest_offset = ?, status = ?,
                    version = version + 1, updated_at = statement_timestamp()
                WHERE user_id = ? AND id = ? AND version = ? AND status IN ('reading', 'paused')
                """, page, position, furthestPage, furthestPosition, offset, status, userId, state.id(), state.version());
    }

    public int complete(long userId, State state) {
        return jdbc.update("""
                UPDATE public.book_reading_sessions SET status = 'completed', completed_at = statement_timestamp(),
                    version = version + 1, updated_at = statement_timestamp()
                WHERE user_id = ? AND id = ? AND version = ? AND status IN ('reading', 'paused')
                """, userId, state.id(), state.version());
    }

    public record PageLength(int number, int length) {}

    // 전체 독서 위치·원본 버전·상태의 내부 저장 모델
    public record State(long id, long bookId, String contentHash, int totalPages, long totalLength,
            int currentPage, int currentPosition, int furthestPage, int furthestPosition, long furthestOffset,
            String status, long version, Instant startedAt, Instant updatedAt, Instant completedAt) {}
}
