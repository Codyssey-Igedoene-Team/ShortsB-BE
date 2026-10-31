-- R1 스키마가 적용된 기존 PostgreSQL DB용 변경 SQL. 백업 후 수동으로 한 번 적용
-- schema.sql로 새로 만든 DB에는 적용하지 않음. 실패 시 전체 롤백
-- 기존 회원·본문 구간·독서·대비 데이터 유지. 기존 도서의 새 필드는 NULL 허용
BEGIN;

ALTER TABLE public.books
    ADD COLUMN external_id varchar(64), -- 제공 자료의 안정적인 작품 ID
    ADD COLUMN catalog_version text, -- 원본 자료 버전
    ADD COLUMN page_count integer, -- 원본 표시 페이지 수
    ADD COLUMN text_sha256 varchar(64), -- 페이지 재결합 본문의 SHA-256
    ADD COLUMN page_joiner text, -- 원본 페이지 사이 구분 문자열
    ADD COLUMN source_metadata jsonb, -- 본문을 제외한 출처·번역·챕터 등 원본 메타데이터
    ADD CONSTRAINT books_external_id_key UNIQUE (external_id),
    ADD CONSTRAINT books_import_metadata_check CHECK (
        (external_id IS NULL AND catalog_version IS NULL AND page_count IS NULL
            AND text_sha256 IS NULL AND page_joiner IS NULL AND source_metadata IS NULL) OR
        (external_id IS NOT NULL AND external_id ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$'
            AND catalog_version IS NOT NULL AND btrim(catalog_version) <> ''
            AND page_count IS NOT NULL AND page_count > 0
            AND text_sha256 IS NOT NULL AND text_sha256 ~ '^[0-9a-f]{64}$'
            AND page_joiner IS NOT NULL AND source_metadata IS NOT NULL
            AND jsonb_typeof(source_metadata) = 'object'
            AND NOT (source_metadata ?| ARRAY['text', 'pages']))
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

COMMIT;
