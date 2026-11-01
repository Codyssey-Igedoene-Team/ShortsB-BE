package com.shortsb.reading.service.impl;

import com.shortsb.book.dto.PageResponse;
import com.shortsb.book.repository.BookRepository;
import com.shortsb.book.repository.BookRepository.BookData;
import com.shortsb.book.service.BookService;
import com.shortsb.common.exception.ApiException;
import com.shortsb.reading.dto.ReadingResponse;
import com.shortsb.reading.exception.ReadingConflictException;
import com.shortsb.reading.repository.ReadingRepository;
import com.shortsb.reading.repository.ReadingRepository.PageLength;
import com.shortsb.reading.repository.ReadingRepository.State;
import com.shortsb.reading.service.ReadingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
// 원본 버전·본인 소유·최대 위치·저장 버전을 검증하는 전체 독서 구현
public class ReadingServiceImpl implements ReadingService {

    private final ReadingRepository repository;
    private final BookRepository books;
    private final BookService content;

    public ReadingServiceImpl(ReadingRepository repository, BookRepository books, BookService content) {
        this.repository = repository;
        this.books = books;
        this.content = content;
    }

    // 동일 회원의 동시 시작과 완료를 직렬화하고 기존 미완료 기록 재사용
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Started start(long userId, long bookId) {
        repository.lockUser(userId);
        repository.lockBook(bookId);
        Layout layout = layout(bookId);
        Optional<State> unfinished = repository.current(userId, bookId);
        if (unfinished.isPresent()) {
            State state = unfinished.get();
            validate(state, layout);
            return new Started(response(state, layout), false);
        }
        long id = repository.insert(userId, bookId, layout.book().contentHash(), layout.lengths().size(), layout.total());
        return new Started(response(owned(userId, id, false), layout), true);
    }

    @Override
    public Optional<ReadingResponse> current(long userId, Long bookId) {
        return repository.current(userId, bookId).map(state -> {
            Layout layout = checked(state);
            return response(state, layout);
        });
    }

    @Override
    public ReadingResponse get(long userId, long sessionId) {
        State state = owned(userId, sessionId, false);
        return response(state, checked(state));
    }

    // 페이지 조회는 위치·진행량·version 변경 없음
    @Override
    public PageResponse page(long userId, long sessionId, int pageNo) {
        State state = owned(userId, sessionId, false);
        checked(state);
        return content.getPage(state.bookId(), pageNo);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReadingResponse progress(long userId, long sessionId, int pageNo, int position, long version, String status) {
        repository.lockUser(userId);
        State state = owned(userId, sessionId, true);
        repository.lockBook(state.bookId());
        Layout layout = checked(state);
        requireVersion(state, layout, version);
        if ("completed".equals(state.status())) {
            throw conflict("READING_ALREADY_COMPLETED", "완료한 기록은 수정할 수 없습니다. 새 독서를 시작해주세요.", state, layout);
        }
        if ((!"reading".equals(status) && !"paused".equals(status)) || pageNo < 1 || pageNo > state.totalPages()
                || position < 0 || position > layout.lengths().get(pageNo - 1).length()) {
            throw invalidPosition();
        }
        PageResponse page = content.getPage(state.bookId(), pageNo);
        // JavaScript 문자열 위치와 같은 UTF-16 단위. 이모지 서로게이트 쌍 중간 거절
        if (position > 0 && position < page.content().length()
                && Character.isHighSurrogate(page.content().charAt(position - 1))
                && Character.isLowSurrogate(page.content().charAt(position))) {
            throw invalidPosition();
        }
        boolean farther = pageNo > state.furthestPage()
                || (pageNo == state.furthestPage() && position > state.furthestPosition());
        int furthestPage = farther ? pageNo : state.furthestPage();
        int furthestPosition = farther ? position : state.furthestPosition();
        long offset = farther ? offset(layout, pageNo, position) : state.furthestOffset();
        if (repository.save(userId, state, pageNo, position, furthestPage, furthestPosition, offset, status) != 1) {
            throw conflict("READING_VERSION_CONFLICT", "저장 상태가 변경됐습니다. 최신 기록을 반영해주세요.",
                    owned(userId, sessionId, true), layout);
        }
        return response(owned(userId, sessionId, false), layout);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReadingResponse complete(long userId, long sessionId, long version) {
        repository.lockUser(userId);
        State state = owned(userId, sessionId, true);
        repository.lockBook(state.bookId());
        Layout layout = checked(state);
        requireVersion(state, layout, version);
        if ("completed".equals(state.status())) {
            return response(state, layout);
        }
        PageResponse last = content.getPage(state.bookId(), state.totalPages());
        if (state.currentPage() != state.totalPages() || state.currentPosition() != last.content().length()
                || state.furthestOffset() != state.totalLength()) {
            throw conflict("READING_NOT_AT_END", "마지막 페이지 끝까지 위치를 저장한 후 완료해주세요.", state, layout);
        }
        if (repository.complete(userId, state) != 1) {
            throw conflict("READING_VERSION_CONFLICT", "저장 상태가 변경됐습니다. 최신 기록을 반영해주세요.",
                    owned(userId, sessionId, true), layout);
        }
        return response(owned(userId, sessionId, false), layout);
    }

    private State owned(long userId, long id, boolean lock) {
        return repository.find(userId, id, lock).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "READING_SESSION_NOT_FOUND", "독서 기록을 찾을 수 없습니다."));
    }

    private Layout checked(State state) {
        Layout layout = layout(state.bookId());
        validate(state, layout);
        return layout;
    }

    // 누적 길이 계산에는 페이지 길이만 사용. 전체 본문을 로딩하지 않음
    private Layout layout(long bookId) {
        BookData book = books.findApproved(bookId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "BOOK_NOT_FOUND", "제공 가능한 도서를 찾을 수 없습니다."));
        List<PageLength> lengths = repository.pageLengths(bookId);
        if (lengths.size() != book.response().totalPageCount()) {
            throw unavailable();
        }
        long total = 0;
        for (int index = 0; index < lengths.size(); index++) {
            PageLength page = lengths.get(index);
            if (page.number() != index + 1 || page.length() <= 0) {
                throw unavailable();
            }
            total += page.length();
        }
        return new Layout(book, lengths, total);
    }

    private void validate(State state, Layout layout) {
        if (!state.contentHash().equals(layout.book().contentHash()) || state.totalPages() != layout.lengths().size()
                || state.totalLength() != layout.total()) {
            throw new ApiException(HttpStatus.CONFLICT, "READING_CONTENT_CHANGED", "본문 버전이 변경됐습니다. 독서 위치 확인이 필요합니다.");
        }
        if (!validPosition(layout, state.currentPage(), state.currentPosition())
                || !validPosition(layout, state.furthestPage(), state.furthestPosition())
                || state.currentPage() > state.furthestPage()
                || (state.currentPage() == state.furthestPage() && state.currentPosition() > state.furthestPosition())
                || state.furthestOffset() != offset(layout, state.furthestPage(), state.furthestPosition())
                || ("completed".equals(state.status()) && (state.currentPage() != state.totalPages()
                    || state.currentPosition() != layout.lengths().getLast().length()
                    || state.furthestOffset() != state.totalLength()))) {
            throw unavailable();
        }
    }

    private boolean validPosition(Layout layout, int page, int position) {
        return page >= 1 && page <= layout.lengths().size() && position >= 0
                && position <= layout.lengths().get(page - 1).length();
    }

    private long offset(Layout layout, int page, int position) {
        long offset = position;
        for (int index = 0; index < page - 1; index++) {
            offset += layout.lengths().get(index).length();
        }
        return offset;
    }

    private void requireVersion(State state, Layout layout, long version) {
        if (version != state.version()) {
            throw conflict("READING_VERSION_CONFLICT", "다른 요청에서 저장했습니다. 최신 기록을 반영해주세요.", state, layout);
        }
        if (state.version() == Long.MAX_VALUE) {
            throw unavailable();
        }
    }

    private ReadingResponse response(State state, Layout layout) {
        BigDecimal percent = BigDecimal.valueOf(state.furthestOffset()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(state.totalLength()), 2, RoundingMode.DOWN);
        return new ReadingResponse(Long.toString(state.id()), layout.book().response(), state.currentPage(),
                state.currentPosition(), state.furthestPage(), state.furthestPosition(), percent, state.status(),
                Long.toString(state.version()), state.startedAt(), state.updatedAt(), state.completedAt());
    }

    private ReadingConflictException conflict(String code, String message, State state, Layout layout) {
        return new ReadingConflictException(code, message, response(state, layout));
    }

    private ApiException invalidPosition() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_READING_POSITION", "페이지·위치·독서 상태를 확인해주세요.");
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "READING_DATA_UNAVAILABLE", "독서 위치·본문 길이를 확인해주세요.");
    }

    private record Layout(BookData book, List<PageLength> lengths, long total) {}
}
