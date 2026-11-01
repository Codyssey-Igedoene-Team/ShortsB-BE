package com.shortsb.reading.service;

import com.shortsb.book.dto.PageResponse;
import com.shortsb.reading.dto.ReadingResponse;
import java.util.Optional;

// 전체 독서 시작·조회·저장·완료 계약
public interface ReadingService {

    Started start(long userId, long bookId);

    Optional<ReadingResponse> current(long userId, Long bookId);

    ReadingResponse get(long userId, long sessionId);

    PageResponse page(long userId, long sessionId, int pageNo);

    ReadingResponse progress(long userId, long sessionId, int pageNo, int position, long version, String status);

    ReadingResponse complete(long userId, long sessionId, long version);

    record Started(ReadingResponse response, boolean created) {}
}
