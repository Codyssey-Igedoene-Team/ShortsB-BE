-- ShortSB 초기 생성 스키마. 수동 관리하며 빈 PostgreSQL DB에 한 번 적용한다.
-- 기존 DB 변경은 별도 마이그레이션으로 적용한다. 재실행으로 기존 구조를 갱신하지 않는다.
-- erd.pgerd는 이전 구조의 참고 자료이며 이 SQL과 자동 동기화되지 않는다.
-- 로그인은 구글 인증 후 서버 메모리 세션과 쿠키를 사용하므로 user_sessions는 생성하지 않는다.
-- 기존 DB의 user_sessions를 자동 삭제하는 스크립트는 아니다.
-- updated_at은 향후 JPA 구현에서 수정 시 갱신한다. DEFAULT now()는 생성 시에만 적용된다.
BEGIN;

-- 회원: 구글 최초 인증 시 생성하며, 이메일 대신 제공자와 sub 조합으로 식별한다.
CREATE TABLE public.users
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 서비스 내부 회원 ID
    email varchar(255) NOT NULL, -- 구글에서 받은 이메일. 필수 수집·검증하며 계정 식별키로 사용하지 않음
    oauth_provider varchar(20) NOT NULL DEFAULT 'google', -- 소셜 로그인 제공자
    oauth_subject varchar(255) NOT NULL, -- 구글 ID 토큰의 변경되지 않는 사용자 식별값 sub
    nickname varchar(50), -- 서비스에서 표시할 이름
    created_at timestamptz NOT NULL DEFAULT now(), -- 회원 생성 시각
    updated_at timestamptz NOT NULL DEFAULT now(), -- 회원 정보 수정 시각. 수정 시 JPA에서 갱신
    CONSTRAINT users_oauth_provider_subject_key UNIQUE (oauth_provider, oauth_subject),
    CONSTRAINT users_email_check CHECK (btrim(email) <> ''),
    CONSTRAINT users_oauth_provider_check CHECK (btrim(oauth_provider) <> ''),
    CONSTRAINT users_oauth_subject_check CHECK (btrim(oauth_subject) <> '')
);

-- 취향: 회원별 장르·주제·분위기 선호를 보관한다. 입력값 목록과 건너뛰기 정책은 별도 확정한다.
CREATE TABLE public.user_preferences
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 취향 레코드 ID
    user_id bigint NOT NULL, -- 취향을 저장한 회원 ID. 회원당 한 행만 허용
    favorite_genres text[] NOT NULL DEFAULT '{}', -- 선호 장르 목록. books.genre와 매칭
    interests text[] NOT NULL DEFAULT '{}', -- 관심 주제 목록. books.topics와 매칭
    preferred_moods text[] NOT NULL DEFAULT '{}', -- 선호 분위기 목록. books.moods와 매칭
    updated_at timestamptz NOT NULL DEFAULT now(), -- 취향 수정 시각. 수정 시 JPA에서 갱신
    CONSTRAINT user_preferences_user_id_key UNIQUE (user_id),
    CONSTRAINT user_preferences_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE
);

-- 도서: 하나의 판본·언어로 확보한 콘텐츠와 출처·추천용 속성을 보관한다.
CREATE TABLE public.books
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 도서 콘텐츠 ID
    title varchar(255) NOT NULL, -- 도서 제목
    author varchar(150) NOT NULL, -- 저자 표시명
    author_bio text, -- 검토한 작가 생애 소개
    author_bio_source_url text, -- 작가 생애 정보의 근거 출처 URL
    description text, -- 도서 소개
    genre varchar(100), -- 최초 추천에 사용하는 대표 장르
    topics text[] NOT NULL DEFAULT '{}', -- 최초 추천에 사용하는 주제 태그 목록
    moods text[] NOT NULL DEFAULT '{}', -- 최초 추천에 사용하는 분위기 태그 목록
    published_year integer, -- 확보한 판본의 출간 연도
    edition text, -- 확보한 판본·번역본 식별 설명
    language varchar(35), -- 본문 언어 코드. 예: ko, en
    cover_url text, -- 표지 이미지 URL
    source_url text, -- 확보한 본문의 출처 URL
    rights_evidence text, -- 해당 판본·번역문을 제공할 수 있는 권한 확인 근거
    content_rights varchar(30) NOT NULL DEFAULT 'unverified', -- 권한 검토 상태: unverified, approved, rejected
    created_at timestamptz NOT NULL DEFAULT now(), -- 도서 등록 시각
    CONSTRAINT books_title_check CHECK (btrim(title) <> ''),
    CONSTRAINT books_author_check CHECK (btrim(author) <> ''),
    CONSTRAINT books_content_rights_check
        CHECK (content_rights IN ('unverified', 'approved', 'rejected')),
    -- 미검토 자료는 임시 저장할 수 있지만, 공개 승인에는 판본·언어·출처·권한 근거가 필요하다.
    CONSTRAINT books_approved_metadata_check CHECK (
        content_rights <> 'approved' OR (
            coalesce(btrim(edition), '') <> '' AND
            coalesce(btrim(language), '') <> '' AND
            coalesce(btrim(source_url), '') <> '' AND
            coalesce(btrim(rights_evidence), '') <> ''
        )
    )
);

-- 본문 구간: 도서의 실제 본문을 순서대로 저장한다. 공개 후 텍스트·분할 변경 시 기존 위치 영향을 검토한다.
CREATE TABLE public.book_segments
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 본문 구간 ID
    book_id bigint NOT NULL, -- 본문이 속한 도서 ID
    segment_no integer NOT NULL, -- 도서 내 구간 순번. 1부터 시작하며 화면 페이지와 구분
    title varchar(255), -- 구간 제목. 없으면 NULL
    content text NOT NULL, -- 실제 원문. AI가 생성한 추천 설명과 구분
    created_at timestamptz NOT NULL DEFAULT now(), -- 구간 등록 시각
    CONSTRAINT book_segments_book_id_segment_no_key UNIQUE (book_id, segment_no),
    CONSTRAINT book_segments_segment_no_check CHECK (segment_no > 0),
    CONSTRAINT book_segments_content_check CHECK (btrim(content) <> ''),
    CONSTRAINT book_segments_book_id_fkey FOREIGN KEY (book_id)
        REFERENCES public.books (id) ON DELETE CASCADE
);

-- 독서 세션: 사용자가 선택한 구간 범위와 재개 위치, 분량 완료 상태를 보관한다. 로그인 세션과 별개다.
CREATE TABLE public.reading_sessions
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 독서 세션 ID
    user_id bigint NOT NULL, -- 독서를 시작한 회원 ID
    book_id bigint NOT NULL, -- 읽는 도서 ID
    start_segment_no integer NOT NULL DEFAULT 1, -- 읽기 범위의 첫 구간 순번
    target_segments integer NOT NULL, -- 목표 내부 구간 수. 사용자 1~10장과의 대응은 미정이므로 상한 10을 적용하지 않음
    completed_segments integer NOT NULL DEFAULT 0, -- 서버가 계산한 완료 구간 수. 재독 시 계산 방식은 별도 확정
    last_segment_no integer, -- 마지막으로 저장한 현재 구간 순번. 위치 저장 전에는 NULL
    last_position integer NOT NULL DEFAULT 0, -- 현재 구간 내 UTF-16 코드 단위 오프셋. 0부터 시작
    status varchar(20) NOT NULL DEFAULT 'reading', -- 독서 상태: reading, paused, completed
    started_at timestamptz NOT NULL DEFAULT now(), -- 독서 시작 시각
    updated_at timestamptz NOT NULL DEFAULT now(), -- 마지막 갱신 시각. 진행 저장·완료 시 JPA에서 갱신
    completed_at timestamptz, -- 선택 분량 완료 시각. 책 전체 완독 시각과 구분
    CONSTRAINT reading_sessions_start_segment_no_check CHECK (start_segment_no > 0),
    CONSTRAINT reading_sessions_target_segments_check CHECK (target_segments > 0),
    CONSTRAINT reading_sessions_completed_segments_check
        CHECK (completed_segments BETWEEN 0 AND target_segments),
    CONSTRAINT reading_sessions_last_position_check CHECK (last_position >= 0),
    CONSTRAINT reading_sessions_last_segment_no_check CHECK (
        last_segment_no IS NULL OR
        last_segment_no::bigint BETWEEN start_segment_no AND start_segment_no::bigint + target_segments - 1
    ),
    CONSTRAINT reading_sessions_initial_position_check CHECK (
        last_segment_no IS NOT NULL OR (last_position = 0 AND completed_segments = 0)
    ),
    CONSTRAINT reading_sessions_status_check CHECK (status IN ('reading', 'paused', 'completed')),
    CONSTRAINT reading_sessions_completion_check CHECK (
        (status = 'completed' AND completed_at IS NOT NULL AND
            completed_segments = target_segments AND last_segment_no IS NOT NULL) OR
        (status <> 'completed' AND completed_at IS NULL)
    ),
    CONSTRAINT reading_sessions_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    -- 독서 기록이 있는 도서는 물리 삭제를 막는다. 제공 중단 시 권한 상태를 변경한다.
    CONSTRAINT reading_sessions_book_id_fkey FOREIGN KEY (book_id)
        REFERENCES public.books (id) ON DELETE RESTRICT,
    CONSTRAINT reading_sessions_start_segment_fkey FOREIGN KEY (book_id, start_segment_no)
        REFERENCES public.book_segments (book_id, segment_no) ON DELETE RESTRICT,
    CONSTRAINT reading_sessions_last_segment_fkey FOREIGN KEY (book_id, last_segment_no)
        REFERENCES public.book_segments (book_id, segment_no) ON DELETE RESTRICT
);
-- 목표 범위 전체의 구간 존재, UTF-16 본문 길이, 최종 구간 끝 도달 여부는 서버에서 추가 검증한다.
-- 완료 후 진행 저장으로 다시 여는 동작과 중복 완료는 서버의 상태 전이·트랜잭션으로 제어한다.

-- 최근 미완료 세션 조회: 회원으로 필터링하고 갱신 시각·ID 내림차순으로 정렬한다.
CREATE INDEX idx_reading_sessions_current
    ON public.reading_sessions (user_id, updated_at DESC, id DESC)
    WHERE status IN ('reading', 'paused');

-- 대비 관계: 공통 주제를 가진 두 도서와 양쪽 근거 구간을 연결한다. 방향은 source에서 target이다.
CREATE TABLE public.book_relations
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 대비 관계 ID
    source_book_id bigint NOT NULL, -- 현재 읽은 내용이 속한 기준 도서 ID
    target_book_id bigint NOT NULL, -- 다음 읽기로 추천할 도서 ID
    source_segment_no integer, -- 기준 도서의 근거 구간 순번. 검토 중에는 NULL 허용
    target_segment_no integer, -- 추천 도서의 근거 구간 순번. 검토 중에는 NULL 허용
    relation_type varchar(30) NOT NULL, -- 대비 유형: perspective, argument, mood, author_life
    common_topic text NOT NULL, -- 두 본문을 연결하는 공통 주제
    reason text NOT NULL, -- 사용자에게 보여줄 추천 이유
    source_evidence text NOT NULL, -- 기준 구간의 본문 근거. author_life는 생애 출처와도 대조
    target_evidence text NOT NULL, -- 추천 구간의 본문 근거. author_life는 생애 출처와도 대조
    score numeric(4, 3), -- 선택적 정렬 점수. 산정 방식과 허용 범위는 별도 확정
    review_status varchar(20) NOT NULL DEFAULT 'pending', -- 관계 검토 상태: pending, approved, rejected
    created_at timestamptz NOT NULL DEFAULT now(), -- 관계 등록 시각
    CONSTRAINT book_relations_source_book_id_target_book_id_relation_type_key
        UNIQUE (source_book_id, target_book_id, relation_type),
    CONSTRAINT book_relations_different_books_check CHECK (source_book_id <> target_book_id),
    CONSTRAINT book_relations_relation_type_check
        CHECK (relation_type IN ('perspective', 'argument', 'mood', 'author_life')),
    CONSTRAINT book_relations_review_status_check
        CHECK (review_status IN ('pending', 'approved', 'rejected')),
    -- 승인된 관계는 양쪽 근거 구간과 비어 있지 않은 설명을 갖춰야 한다.
    CONSTRAINT book_relations_approved_evidence_check CHECK (
        review_status <> 'approved' OR (
            source_segment_no IS NOT NULL AND target_segment_no IS NOT NULL AND
            btrim(common_topic) <> '' AND btrim(reason) <> '' AND
            btrim(source_evidence) <> '' AND btrim(target_evidence) <> ''
        )
    ),
    CONSTRAINT book_relations_source_book_id_fkey FOREIGN KEY (source_book_id)
        REFERENCES public.books (id) ON DELETE CASCADE,
    CONSTRAINT book_relations_target_book_id_fkey FOREIGN KEY (target_book_id)
        REFERENCES public.books (id) ON DELETE CASCADE,
    CONSTRAINT book_relations_source_segment_fkey FOREIGN KEY (source_book_id, source_segment_no)
        REFERENCES public.book_segments (book_id, segment_no) ON DELETE RESTRICT,
    CONSTRAINT book_relations_target_segment_fkey FOREIGN KEY (target_book_id, target_segment_no)
        REFERENCES public.book_segments (book_id, segment_no) ON DELETE RESTRICT
);
-- 추천 시 양쪽 도서의 권한 승인과 기준 근거 구간의 실제 읽기 완료 여부를 서버에서 확인한다.
-- 검수 시 근거가 지정 구간에 있는지 확인한다. 복수 구간·구간 내 세부 범위는 현재 모델에 포함하지 않는다.

-- 추천 기록: 기존 대비 추천 이벤트 구조를 유지한다. 성과 측정 범위와 이벤트 계약 확장은 보류한다.
CREATE TABLE public.recommendation_logs
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 추천 이벤트 ID
    user_id bigint NOT NULL, -- 이벤트를 발생시킨 회원 ID
    reading_session_id bigint NOT NULL, -- 추천의 기준이 된 독서 세션 ID
    relation_id bigint NOT NULL, -- 노출·선택한 대비 관계 ID
    action varchar(20) NOT NULL DEFAULT 'shown', -- 이벤트 값. 초안은 shown, clicked, started이며 계약 확정은 보류
    created_at timestamptz NOT NULL DEFAULT now(), -- 이벤트 기록 시각
    CONSTRAINT recommendation_logs_user_id_fkey FOREIGN KEY (user_id)
        REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT recommendation_logs_reading_session_id_fkey FOREIGN KEY (reading_session_id)
        REFERENCES public.reading_sessions (id) ON DELETE CASCADE,
    CONSTRAINT recommendation_logs_relation_id_fkey FOREIGN KEY (relation_id)
        REFERENCES public.book_relations (id) ON DELETE CASCADE
);

COMMIT;
