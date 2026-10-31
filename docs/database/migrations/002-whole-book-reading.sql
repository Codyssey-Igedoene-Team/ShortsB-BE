-- 001-content-embeddings.sql까지 적용한 기존 DB용. 백업 후 한 번 수동 적용
-- 빈 DB는 schema.sql만 적용. 기존 독서·추천 기록 보존
BEGIN;

-- R3 전체 독서 시작
-- 전체 독서: 발췌와 분리한 책 전체의 현재/최대 위치·저장 버전
CREATE TABLE public.book_reading_sessions
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 전체 독서 세션 내부 ID
    user_id bigint NOT NULL REFERENCES public.users (id) ON DELETE CASCADE, -- 기록 소유 회원
    book_id bigint NOT NULL REFERENCES public.books (id) ON DELETE RESTRICT, -- 전체 독서 작품 ID
    content_sha256 varchar(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'), -- 시작 당시 전체 본문 해시
    total_pages integer NOT NULL CHECK (total_pages > 0), -- 시작 당시 전체 표시 페이지 수
    total_utf16_length bigint NOT NULL CHECK (total_utf16_length > 0), -- 페이지 구분자 제외 전체 본문 길이
    current_page_no integer NOT NULL DEFAULT 1, -- 이어 읽을 현재 페이지. 1부터 시작
    current_position integer NOT NULL DEFAULT 0 CHECK (current_position >= 0), -- 현재 페이지 내 UTF-16 위치
    furthest_page_no integer NOT NULL DEFAULT 1, -- 최대 도달 페이지
    furthest_position integer NOT NULL DEFAULT 0 CHECK (furthest_position >= 0), -- 최대 페이지 내 UTF-16 위치
    furthest_offset bigint NOT NULL DEFAULT 0, -- 전체 본문 기준 최대 누적 UTF-16 위치
    status varchar(20) NOT NULL DEFAULT 'reading' CHECK (status IN ('reading', 'paused', 'completed')), -- 전체 독서 상태
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0), -- 진행/완료 갱신마다 증가하는 저장 버전
    started_at timestamptz NOT NULL DEFAULT now(), -- 전체 독서 시작 시각
    updated_at timestamptz NOT NULL DEFAULT now(), -- 마지막 진행·완료 저장 시각
    completed_at timestamptz, -- 책 전체 완료 시각. 미완료면 NULL
    CHECK (current_page_no BETWEEN 1 AND total_pages AND furthest_page_no BETWEEN 1 AND total_pages),
    CHECK ((current_page_no, current_position) <= (furthest_page_no, furthest_position)),
    CHECK (furthest_offset BETWEEN 0 AND total_utf16_length),
    CHECK ((status = 'completed' AND completed_at IS NOT NULL AND current_page_no = total_pages
                AND current_page_no = furthest_page_no AND current_position = furthest_position
                AND furthest_offset = total_utf16_length) OR (status <> 'completed' AND completed_at IS NULL)),
    FOREIGN KEY (book_id, current_page_no) REFERENCES public.book_pages (book_id, page_no) ON DELETE RESTRICT,
    FOREIGN KEY (book_id, furthest_page_no) REFERENCES public.book_pages (book_id, page_no) ON DELETE RESTRICT
);
-- 페이지 내 위치 상한·누적 위치·마지막 끝 도달은 서버 트랜잭션에서 추가 검증

-- 한 회원이 같은 책에 유지하는 미완료 전체 독서는 하나
CREATE UNIQUE INDEX uq_book_reading_sessions_unfinished
    ON public.book_reading_sessions (user_id, book_id) WHERE status IN ('reading', 'paused');

-- 이어 읽기: 본인의 최근 미완료 기록
CREATE INDEX idx_book_reading_sessions_current
    ON public.book_reading_sessions (user_id, updated_at DESC, id DESC) WHERE status IN ('reading', 'paused');
-- R3 전체 독서 끝

COMMIT;
