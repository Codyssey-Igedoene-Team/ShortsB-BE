# 대비 후보·검수 근거 등록

상태: 사용자가 GPU 사전 임베딩·로컬 DB 등록·첫 추천·전체 독서 시작/저장/재개·백야 → B사감과 러브레터 대비 응답 성공을 보고했다. CSV 45개 후보의 로컬 시연용 SQL을 추가했다. 추가 SQL의 DB 반영·나머지 대비 응답·프런트 통합은 미확인이다. Codex는 Spring 빌드·테스트·DB 적용·git을 실행하지 않았다.

## 로컬 시연용 45개 관계 SQL

사용자가 후보별 짧은 본문 구절 확인과 SQL 파일 생성을 승인하여 [contrast-seed.sql](contrast-seed.sql)을 작성했다. 전체 본문은 출력하지 않았다. 제공된 45개 방향성 후보·6개 축을 유지하고, 각 구절에 맞게 연결 주제와 설명을 작성했다. CSV 주제를 양쪽에서 입증된 주제로 그대로 복사하지 않았다. 결말 대비는 마지막 페이지 구절을 사용했다.

- 새 API 키·DB 환경변수는 필요 없다. 기존 로컬 `shortsb` DB의 pgAdmin Query Tool에서 SQL **전체를 선택해 실행**한다. 기존 스키마/003 및 도서 등록이 선행되어야 한다.
- 실행은 AI가 작성한 구절·설명에 대한 사용자의 **로컬 시연 승인**이다. `reviewed_by`는 `local-demo-user:AI-assisted`로 기록한다. 사람의 전체 본문 검수나 공개 이용 조건 검토가 완료됐다는 뜻은 아니다.
- DB 숫자 ID를 고정하지 않고 작품 외부 ID로 연결한다. 각 작품의 예상 전체 본문 해시·현재 권한 승인·실제 페이지·정확한 구절 포함을 확인한다. 하나라도 불일치하면 전체 반영을 중단한다. 실패한 트랜잭션은 `ROLLBACK;`으로 종료하고 원인을 확인한다.
- 구절 안의 줄바꿈은 `E'…\n…'` 형식으로 고정해 Windows의 SQL 파일 CRLF 변환과 구분한다. 불일치 시 오류 DETAIL에 작품 외부 ID·축·실패 항목을 표시하고 본문은 출력하지 않는다. 오류 후 재실행할 때는 `ROLLBACK;`을 먼저 실행하고 수정된 파일을 새로 열어 전체를 실행한다.
- 후보·페이지 처리는 준비 도구와 동일한 등록 잠금으로 보호한다. 새 관계는 승인 상태로 추가하고 기존 `pending`은 갱신한다. 기존 `approved`/`rejected`는 내용·페이지·검수자를 보존한다. 앞서 등록한 백야 → B사감 관계도 유지한다.
- 기존 승인 1건 외에 다른 관계가 없다면 44건 추가·후보 범위의 승인 총 45건을 예상한다. 모두 승인 상태라면 작품마다 나가는 관계 3건이다. 기존 제외 관계가 있으면 승인 수는 더 적으며 SQL의 상태별 집계로 드러난다. 재실행 시 이미 승인된 관계는 변경하지 않는다.
- 양쪽 근거를 한 페이지씩 지정해 요청 1~10쪽에 담을 수 있게 했다. 실제 첫 발췌에 기준 근거 페이지가 포함되어야 한다. 작품별 3개 관계가 모든 페이지·프롬프트를 지원한다는 뜻은 아니다.
- 현재 스키마는 `(기준 작품, 대상 작품, 축)`당 한 행이다. 동일 쌍/축의 여러 구간을 동시에 추가하는 확대는 별도 스키마·등록 계약 변경이 필요하다. 이번 작업에서는 변경하지 않았다.

SQL은 처리한 행 수·후보 범위 상태별 수·실제 근거 페이지와 본문 버전 일치 여부를 출력한다. 등록 후 전체 상태는 다음처럼 확인한다. 다른 기존 관계가 있다면 전체 집계는 45건보다 클 수 있다.

```sql
SELECT review_status, count(*) AS relation_count
FROM public.book_contrast_relations
GROUP BY review_status;

SELECT s.title AS source_title, t.title AS target_title, r.axis,
       r.source_start_page_no, r.target_start_page_no
FROM public.book_contrast_relations r
JOIN public.books s ON s.id = r.source_book_id
JOIN public.books t ON t.id = r.target_book_id
WHERE r.review_status = 'approved'
ORDER BY s.title, t.title;
```

같은 로그인 세션의 유효한 `previewId`로 대비 API를 재호출하면 즉시 반영된다. 스프링 재시작은 필요 없다. 발췌에 해당 근거가 없으면 계속 `available=false`일 수 있다. 사용자 확인이 끝난 백야 1~3쪽은 백야 3쪽 → B사감 1쪽 관계로 계속 확인할 수 있다.

## 저장과 준비

- 기존 book_relations·recommendation_logs는 보존한다. 신규 book_contrast_relations는 양쪽 책·6개 축·본문 해시·근거 페이지/구절·설명·검수 상태를 저장한다.
- 빈 DB는 schema.sql만 적용한다. 기존 DB는 001·002 적용 후 migrations/003-contrast-relations.sql을 한 번 적용한다. Codex는 적용하지 않았다.
- 도서는 importCatalog로 먼저 등록한다. 후보 pending 등록에는 작품 권한 승인이 필요하지 않다. approved 등록에는 양쪽 권한 승인이 필요하다.
- 사용할 환경변수는 기존 SPRING_DATASOURCE_URL·SPRING_DATASOURCE_USERNAME·SPRING_DATASOURCE_PASSWORD다. 새 API 키·GPU 연결·Google 키는 등록 명령에 필요 없다.

## 후보 양식 생성

scripts/prepare_contrast_reviews.py는 표준 Python csv로 자료를 읽는다. 제목 공백을 정규화해 대상 외부 ID를 연결하고 결/시선/어조/태도/결말/시대·배경을 texture/perspective/tone/attitude/ending/setting으로 변환한다. 후보 1~3의 순서를 점수로 변환하지 않고 역방향을 생성하지 않는다. 중복·동일 작품·알 수 없는 축/제목은 실패한다.

```bash
python3 scripts/prepare_contrast_reviews.py --input docs/shortb_axes.csv --output docs/database/contrast-review-template.jsonl --catalog-metadata ../ShortsB-AI/data/catalog-v1/catalog-metadata.json
```

현재 15개 작품의 45개 방향성 후보를 위 경로에 생성했다. 모두 pending이다. 선택적 catalog-metadata는 prepare_catalog.py가 만든 **본문 제외** 메타데이터 파일이며 book.textSHA256만 양식에 연결한다. books.json·TXT를 입력하지 않는다. 기존 출력 파일은 덮어쓰지 않으므로 다시 만들 때 새 경로를 선택한다.

양식의 commonTopic은 기준 작품 CSV 주제의 제안값이다. 양쪽의 공통 주제로 검수한 결과가 아니므로 승인 전에 사람이 수정·확인한다. reason·sourceEvidence·targetEvidence·reviewedBy는 자동 작성하지 않는다.

## 사람 검수 입력

양식을 별도 작업 파일로 복사해 검수할 행을 남기고 아래 필드를 채운다. 이 파일은 근거 구절을 포함하므로 에이전트가 전체 파일을 열거나 출력하지 않는다.

| 필드 | 의미 |
| --- | --- |
| sourceBookId / targetBookId | CSV·도서 자료의 외부 문자열 ID. DB bigint ID가 아님 |
| axis | texture / perspective / tone / attitude / ending / setting |
| commonTopic | 양쪽 구절을 연결하는 검수한 공통 주제 |
| reason | 실제 양쪽 발췌로 뒷받침하는 대비 설명. 최대 UTF-16 4,000단위 |
| sourceContentHash / targetContentHash | 검수한 두 작품의 전체 textSHA256. approved는 필수 |
| sourceEvidence / targetEvidence | {startPageNo,endPageNo,quote}. 1-based·끝 포함·연속 최대 10쪽 |
| reviewStatus | pending / approved / rejected |
| reviewedBy | 사람이 입력한 검수자 표시. approved/rejected에서 필수 |

구절은 인용 부호나 생략 표시를 새로 붙이지 않은 정확한 원문 문자열이다. 지정 페이지를 원본 pageJoiner로 연결한 문자열에 그대로 포함돼야 한다. 한 페이지 근거도 시작/끝을 같은 번호로 지정한다. 챕터의 0-based 인덱스를 페이지 번호로 사용하지 않는다.

approved는 위 필수 정보와 양쪽 작품 권한을 확인해 사람이 명시한다. 등록 프로그램은 원본 해시·페이지 범위·구절 포함만 검사하며 대비의 의미·설명 진실성을 판단하지 않는다. 형식 검사 성공을 사람 승인으로 간주하지 않는다. rejected는 검수자가 필수이며 후보로 노출하지 않는다. pending의 근거/해시는 null일 수 있다. JSONL의 각 행에는 양식의 필드 이름 11개를 유지한다.

## 사용자 실행

```bash
./gradlew importContrasts --args='--input docs/database/contrast-review-template.jsonl --validate-only'
./gradlew importContrasts --args='--input docs/database/contrast-review-template.jsonl --apply'
```

위 두 명령은 **모두 DB 접속이 필요**하다. validate-only는 도서·해시·권한·실제 구절을 DB에서 조회해 대조하고 쓰지 않는다. 후보 양식을 그대로 apply해도 pending만 등록된다. 검수자가 작성한 파일을 적용하려면 --input을 그 파일 경로로 지정한다. 명령은 일반 서버 시작과 분리되며 웹/Google/GPU를 실행하지 않는다. .env 파일은 자동으로 읽지 않는다.

apply는 전체 파일을 하나의 트랜잭션으로 처리한다. 하나라도 실패하면 전체 롤백한다. 동시 등록 advisory lock과 books/book_pages의 SHARE 잠금으로 근거 확인 중 원본 변경을 막는다. 잠금 대기는 5초, 명령 트랜잭션은 120초다. 실패 시 예외 종류와 고정 안내만 출력하고 구절·원래 SQL 메시지·비밀값은 출력하지 않는다. SQL/바인딩 TRACE 로깅을 켜지 않는다.

동일 source/target/axis의 pending 재등록은 DO NOTHING으로 기존 승인/제외/검수 내용을 보존한다. approved/rejected 파일은 명시적인 검수 상태와 내용으로 해당 관계를 갱신하며 검수 시각을 기록한다. 역방향과 다른 축은 별도 행이다. 과거 승인 이력 전체를 보관하는 감사 로그는 이번 범위가 아니다.

## 대비 API 동작

1. 같은 로그인 세션의 첫 previewId로 POST /api/previews/{previewId}/contrast를 호출한다. POST CSRF 토큰이 필요하며 본문은 없다.
2. 첫 발췌의 소유자·작품 권한·본문 버전·실제 페이지를 확인한다.
3. 승인 관계 중 기준 근거 전체가 첫 발췌에 포함되고 대상 근거를 min(원래 요청,대상 전체)에 담을 수 있는 관계만 남긴다.
4. 같은 모델/자료 버전의 사전 본문 벡터를 사용한다. 첫 발췌 페이지 평균과 대상 근거를 포함하는 연속 창 평균을 각각 정규화해 코사인 유사도로 정렬한다. 카드 필터·프롬프트 임베딩·새 GPU 호출은 없다. 동점은 관계 ID·대상 시작 페이지 오름차순이다.
5. 선택한 양쪽 구절을 원본에서 다시 대조하고 대상 연속 페이지를 제공한다. 관계가 없으면 200 available=false, 필요한 벡터가 없으면 503이다.

평균 벡터 정규화가 불가능해도 503이며 임의 추천으로 대체하지 않는다. 벡터 유사도는 공통 의미 정렬용이고 대비 주장은 사람의 승인 축/설명에 근거한다. 잘못된 승인 구절은 503 / CONTRAST_EVIDENCE_INVALID다.

첫 발췌가 벡터 검색으로 생성됐다면 그 modelFingerprint를 유지한다. ANY 단독의 첫 발췌는 활성 모델을 사용하며 자료 버전이 다르면 준비 부족으로 거절한다. 새 두 번째 previewId나 세션 항목은 생성하지 않는다. sourcePreviewId로 연결하고 다시 요청하면 현재 승인 자료로 재계산한다. 응답의 contrast.book.bookId 또는 첫 발췌의 book.bookId로 R3 전체 독서를 시작한다.

요청 10쪽·첫 작품 8쪽·대상 작품 20쪽이면 두 번째는 10쪽이다. 근거가 2쪽인데 원래 요청이 1쪽이면 해당 관계를 사용하지 않는다. 근거/후보 없음 때문에 첫 책의 전체 독서를 막지 않는다.
