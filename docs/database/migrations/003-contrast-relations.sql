-- 001·002까지 적용한 기존 DB용. 한 번 수동 적용. 기존 book_relations·로그는 보존
BEGIN;

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
