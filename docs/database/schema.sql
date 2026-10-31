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

-- 기존 취향: 장르·주제·분위기 배열 보관용. 현재 필수 카드·선택 프롬프트 입력과 구분
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
    external_id varchar(64), -- 제공 자료의 안정적인 작품 ID. 기존 수동 자료는 NULL 허용
    catalog_version text, -- 원본 자료 버전
    page_count integer, -- 원본 표시 페이지 수
    text_sha256 varchar(64), -- 페이지 재결합 본문의 SHA-256
    page_joiner text, -- 원본 페이지 사이 구분 문자열
    source_metadata jsonb, -- 본문을 제외한 출처·번역·챕터 등 원본 메타데이터
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
    CONSTRAINT books_external_id_key UNIQUE (external_id),
    CONSTRAINT books_import_metadata_check CHECK (
        (external_id IS NULL AND catalog_version IS NULL AND page_count IS NULL
            AND text_sha256 IS NULL AND page_joiner IS NULL AND source_metadata IS NULL) OR
        (external_id IS NOT NULL AND external_id ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$'
            AND catalog_version IS NOT NULL AND btrim(catalog_version) <> ''
            AND page_count IS NOT NULL AND page_count > 0
            AND text_sha256 IS NOT NULL AND text_sha256 ~ '^[0-9a-f]{64}$'
            AND page_joiner IS NOT NULL AND source_metadata IS NOT NULL
            AND jsonb_typeof(source_metadata) = 'object'
            AND NOT (source_metadata ?| ARRAY['text', 'pages']))
    ),
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

-- 기존 본문 구간: R3/R4 전환 전 독서·대비 FK 유지용. 신규 등록은 book_pages·book_search_segments 사용.
-- 도서의 실제 본문을 순서대로 저장한다. 공개 후 텍스트·분할 변경 시 기존 위치 영향을 검토한다.
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

-- 기존 독서 세션: 선택 구간 기준 구조. 전체 책·현재/최대 위치·버전 전환은 R3에서 별도 반영
-- 로그인 세션과 별개인 독서 기록
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

-- 기존 대비 관계: 양쪽 근거 구간 연결 구조. 페이지 범위·6개 대비 축 전환은 R4에서 별도 반영
-- 공통 주제를 가진 두 도서의 방향성 관계. source에서 target 방향
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

-- R2 추가 테이블 시작
-- 표시 페이지: 제공 자료의 pages 배열을 순서와 내용 그대로 저장
CREATE TABLE public.book_pages
(
    book_id bigint NOT NULL, -- 서비스 내부 도서 ID
    page_no integer NOT NULL CHECK (page_no > 0), -- 표시 페이지 번호. 1부터 시작
    content text NOT NULL CHECK (btrim(content) <> ''), -- 원본 페이지 본문
    content_sha256 varchar(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'), -- 페이지 UTF-8 본문의 SHA-256
    utf16_length integer NOT NULL CHECK (utf16_length > 0), -- 페이지 내 위치 상한. Java 문자열 길이
    PRIMARY KEY (book_id, page_no),
    FOREIGN KEY (book_id) REFERENCES public.books (id) ON DELETE RESTRICT
);

-- 검색 구간: 본문 중복 없이 원본 페이지와 UTF-16 범위 연결
CREATE TABLE public.book_search_segments
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 검색 구간 내부 ID
    external_id varchar(128) NOT NULL UNIQUE CHECK (btrim(external_id) <> ''), -- 사전 임베딩 입력의 안정 ID
    book_id bigint NOT NULL, -- 구간이 속한 도서 ID
    page_no integer NOT NULL, -- 구간이 속한 표시 페이지 번호
    start_position integer NOT NULL CHECK (start_position >= 0), -- 페이지 내 UTF-16 시작 위치. 포함
    end_position integer NOT NULL CHECK (end_position > start_position), -- 페이지 내 UTF-16 끝 위치. 제외
    content_sha256 varchar(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'), -- 구간 원문의 SHA-256
    UNIQUE (book_id, page_no, start_position, end_position),
    FOREIGN KEY (book_id, page_no) REFERENCES public.book_pages (book_id, page_no) ON DELETE RESTRICT
);
-- 페이지 수·연속성·UTF-16 상한·본문 해시는 등록 서비스에서 추가 검증

-- 임베딩 모델: Python과 공유하는 불변 설정. 장치·생성 시각은 호환성 지문에서 제외
CREATE TABLE public.embedding_models
(
    fingerprint varchar(64) PRIMARY KEY CHECK (fingerprint ~ '^[0-9a-f]{64}$'), -- 모델 설정의 정규 JSON SHA-256
    model_id text NOT NULL CHECK (btrim(model_id) <> ''), -- 공개 모델 저장소 ID
    revision varchar(40) NOT NULL CHECK (revision ~ '^[0-9a-f]{40}$'), -- 고정 모델 커밋
    dimension integer NOT NULL CHECK (dimension > 0), -- 벡터 차원
    normalized boolean NOT NULL CHECK (normalized), -- L2 정규화 여부. true만 허용
    configuration jsonb NOT NULL CHECK (jsonb_typeof(configuration) = 'object'), -- 토큰 상한·전처리·접두사·라이브러리 등 지문 대상 설정
    created_at timestamptz NOT NULL DEFAULT now(), -- 최초 등록 시각
    UNIQUE (fingerprint, dimension)
);

-- 본문 벡터: 검색 구간과 정확히 같은 원문에서 GPU로 생성한 document 임베딩
CREATE TABLE public.book_segment_embeddings
(
    segment_id bigint NOT NULL REFERENCES public.book_search_segments (id) ON DELETE RESTRICT, -- 원본 검색 구간 ID
    model_fingerprint varchar(64) NOT NULL, -- 벡터를 생성한 모델 설정 지문
    dimension integer NOT NULL, -- 모델 FK 및 벡터 길이 검증용 차원
    embedding double precision[] NOT NULL, -- 정규화된 document 벡터
    PRIMARY KEY (segment_id, model_fingerprint),
    FOREIGN KEY (model_fingerprint, dimension) REFERENCES public.embedding_models (fingerprint, dimension),
    CONSTRAINT book_segment_embeddings_vector_check CHECK (
        array_ndims(embedding) = 1 AND array_lower(embedding, 1) = 1
        AND cardinality(embedding) = dimension AND array_position(embedding, NULL) IS NULL
        AND NOT ('NaN'::double precision = ANY (embedding))
        AND NOT ('Infinity'::double precision = ANY (embedding))
        AND NOT ('-Infinity'::double precision = ANY (embedding))
    )
);

-- 카드 벡터: ANY를 제외한 세 카드의 검색 문장과 사전 query 임베딩
CREATE TABLE public.card_embeddings
(
    model_fingerprint varchar(64) NOT NULL, -- 본문 벡터와 같은 모델 설정 지문
    card_code varchar(20) NOT NULL CHECK (card_code IN ('WARM', 'HUMOR', 'THOUGHTFUL')), -- 고정 카드 코드
    input_id varchar(128) NOT NULL CHECK (btrim(input_id) <> ''), -- 카드 검색 문장의 버전 포함 원본 ID
    query_text text NOT NULL CHECK (btrim(query_text) <> ''), -- 사전 생성에 사용한 카드 검색 문장
    content_sha256 varchar(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'), -- 검색 문장의 SHA-256
    dimension integer NOT NULL, -- 모델 FK 및 벡터 길이 검증용 차원
    embedding double precision[] NOT NULL, -- 정규화된 query 벡터
    PRIMARY KEY (model_fingerprint, card_code),
    FOREIGN KEY (model_fingerprint, dimension) REFERENCES public.embedding_models (fingerprint, dimension),
    CONSTRAINT card_embeddings_vector_check CHECK (
        array_ndims(embedding) = 1 AND array_lower(embedding, 1) = 1
        AND cardinality(embedding) = dimension AND array_position(embedding, NULL) IS NULL
        AND NOT ('NaN'::double precision = ANY (embedding))
        AND NOT ('Infinity'::double precision = ANY (embedding))
        AND NOT ('-Infinity'::double precision = ANY (embedding))
    )
);

-- 추천 설정: 본문 전체와 카드 3종 등록에 성공한 트랜잭션에서만 사용할 모델 전환
CREATE TABLE public.recommendation_configuration
(
    id integer PRIMARY KEY CHECK (id = 1), -- 단일 추천 설정 행의 고정 ID
    model_fingerprint varchar(64) NOT NULL REFERENCES public.embedding_models (fingerprint), -- 검색과 온라인 query에 사용할 모델 지문
    catalog_version text NOT NULL CHECK (btrim(catalog_version) <> ''), -- 검증된 도서 자료 버전
    catalog_source_sha256 varchar(64) NOT NULL CHECK (catalog_source_sha256 ~ '^[0-9a-f]{64}$'), -- 준비 manifest의 원본 자료 파일 SHA-256
    book_count integer NOT NULL CHECK (book_count > 0), -- 해당 등록 묶음의 작품 수
    segment_count integer NOT NULL CHECK (segment_count > 0), -- 해당 등록 묶음의 본문 벡터 수
    updated_at timestamptz NOT NULL DEFAULT now() -- 추천 설정 반영 시각
);
-- 권한 승인·추천 공개는 별도. 모델 설정 등록만으로 미검토 작품을 공개하지 않음
-- R2 추가 테이블 끝

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

-- R4 대비 관계 시작
-- 방향성 대비 관계: 실제 양쪽 근거와 사람의 검수 상태 저장
CREATE TABLE public.book_contrast_relations
(
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 대비 관계 내부 ID
    source_book_id bigint NOT NULL REFERENCES public.books (id) ON DELETE RESTRICT, -- 기준 작품 ID
    target_book_id bigint NOT NULL REFERENCES public.books (id) ON DELETE RESTRICT, -- 대비 작품 ID
    axis varchar(30) NOT NULL CHECK (axis IN ('texture','perspective','tone','attitude','ending','setting')), -- 결·시선·어조·태도·결말·시대/배경
    common_topic text, -- 사람이 확인한 공통 연결 주제. 후보 단계에서는 미확정 가능
    reason text, -- 검수한 대비 설명
    source_content_sha256 varchar(64) CHECK (source_content_sha256 ~ '^[0-9a-f]{64}$'), -- 검수 당시 기준 작품 본문 해시
    target_content_sha256 varchar(64) CHECK (target_content_sha256 ~ '^[0-9a-f]{64}$'), -- 검수 당시 대상 작품 본문 해시
    source_start_page_no integer, -- 기준 근거 첫 페이지. 1-based
    source_end_page_no integer, -- 기준 근거 마지막 페이지. 포함
    source_evidence text, -- 기준 범위에 실제 존재하는 검수 구절
    target_start_page_no integer, -- 대상 근거 첫 페이지. 1-based
    target_end_page_no integer, -- 대상 근거 마지막 페이지. 포함
    target_evidence text, -- 대상 범위에 실제 존재하는 검수 구절
    review_status varchar(20) NOT NULL DEFAULT 'pending' CHECK (review_status IN ('pending','approved','rejected')), -- 검토 대기·승인·제외
    reviewed_by varchar(100), -- 사람이 입력한 검수자 식별 표시
    reviewed_at timestamptz, -- 명시적으로 승인/제외한 시각
    created_at timestamptz NOT NULL DEFAULT now(), -- 후보 최초 등록 시각
    updated_at timestamptz NOT NULL DEFAULT now(), -- 마지막 검수 상태 반영 시각
    UNIQUE (source_book_id, target_book_id, axis),
    CHECK (source_book_id <> target_book_id),
    CHECK ((source_start_page_no IS NULL AND source_end_page_no IS NULL) OR
        (source_start_page_no IS NOT NULL AND source_end_page_no IS NOT NULL AND source_start_page_no > 0
            AND source_end_page_no >= source_start_page_no AND source_end_page_no - source_start_page_no < 10)),
    CHECK ((target_start_page_no IS NULL AND target_end_page_no IS NULL) OR
        (target_start_page_no IS NOT NULL AND target_end_page_no IS NOT NULL AND target_start_page_no > 0
            AND target_end_page_no >= target_start_page_no AND target_end_page_no - target_start_page_no < 10)),
    CHECK (review_status <> 'approved' OR (
        source_content_sha256 IS NOT NULL AND target_content_sha256 IS NOT NULL
        AND source_start_page_no IS NOT NULL AND target_start_page_no IS NOT NULL
        AND coalesce(btrim(common_topic),'') <> '' AND coalesce(btrim(reason),'') <> ''
        AND coalesce(btrim(source_evidence),'') <> '' AND coalesce(btrim(target_evidence),'') <> ''
        AND coalesce(btrim(reviewed_by),'') <> '' AND reviewed_at IS NOT NULL)),
    CHECK (review_status <> 'rejected' OR (coalesce(btrim(reviewed_by),'') <> '' AND reviewed_at IS NOT NULL)),
    FOREIGN KEY (source_book_id, source_start_page_no) REFERENCES public.book_pages (book_id,page_no),
    FOREIGN KEY (source_book_id, source_end_page_no) REFERENCES public.book_pages (book_id,page_no),
    FOREIGN KEY (target_book_id, target_start_page_no) REFERENCES public.book_pages (book_id,page_no),
    FOREIGN KEY (target_book_id, target_end_page_no) REFERENCES public.book_pages (book_id,page_no)
);
-- 구절의 실제 포함·본문 버전·권한은 등록 서비스와 추천 요청에서 추가 검증
-- R4 대비 관계 끝

COMMIT;
