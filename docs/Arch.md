# 백엔드 구조

## 현재 확인된 구성

| 항목 | 구성 |
| --- | --- |
| 언어·서버 | Java 21, Spring Boot 4.1.2, Spring MVC |
| 데이터 접근 | Spring Data JPA, 도서·추천·독서·수동 등록용 JdbcTemplate, PostgreSQL 드라이버 |
| 보안·검증 | Spring Security, Validation |
| 외부 HTTP 연동 | RestClient 관련 의존성 |
| API 문서 | springdoc OpenAPI UI 3.2.0 |
| 연동 클라이언트 | React |

`com.shortsb` 아래에 R1의 `auth`·`user`·`common` 구현과 R2의 `embedding` 내부 연결 서비스와 `book` 수동 등록 기능이 있다. 사용자가 R1 전체 검증 완료를 보고하여 인증 단계를 완료 처리했다. 개별 실행 로그는 별도 수집하지 않았으며 Codex는 Spring 빌드·테스트를 실행하지 않았다. 첫 추천·발췌 재조회·승인 도서 정보 API 코드를 작성했으며 실행·DB/GPU 통합은 미검증이다. 전체 독서 API와 저장 SQL도 작성했으며 실제 실행·DB 적용은 미검증이다. 대비 API·검수 등록 코드와 SQL도 작성했으며 실제 근거 승인·실행 검증은 대기 중이다.

상위 `ShortsB-AI`에는 KURE-v1 공통 encoder·FastAPI health/embeddings·도서 준비·JSONL 사전 생성·산출물 검증 도구를 작성했다. 합성 encoder를 사용한 Python/HTTP 검사 20개는 통과했으며, 이는 Spring 빌드·테스트 실행과 별개다. 15개 작품·864페이지 입력 검증을 마쳤다. 사용자 GPU의 합성 문장 임베딩은 확인했지만 새 앱의 GPU 기동·실제 도서 임베딩·Spring 통합은 미검증이다.

## 구현 구조안

- Spring 업무 애플리케이션과 FastAPI 임베딩 프로세스로 역할을 분리한다. 사전 벡터 생성은 같은 encoder를 사용하는 Python 스크립트로 수행한다. FastAPI 최소 코드는 별도 ShortsB-AI에 작성했고 실제 서버 기동·연동 확인은 남아 있다.
- 도메인별 `auth`, `user`, `book`, `reading`, `recommendation` 패키지를 둔다.
- 도메인 내부를 역할별 하위 패키지로 나눈다. 현재 `auth`는 `config`, `controller`, `dto`, `security`, `service/impl`, `user`는 `controller`, `dto`, `entity`, `repository`, `service/impl`, `common`은 `config`, `dto`, `exception`으로 구성한다.
- `book`은 `command`, `controller`, `dto`, `repository`, `service/impl` 구조다. CatalogImportCommand는 웹 앱과 분리된 수동 명령이며 CatalogImportReader가 파일을 검증하고 CatalogImportServiceImpl이 트랜잭션 등록을 처리한다. BookController/BookServiceImpl은 승인 도서 정보와 저장 발췌 범위의 해시·연속 페이지를 조회한다.
- `recommendation`은 `config`, `controller`, `dto`, `repository`, `service/impl` 구조다. PreviewServiceImpl은 네 입력 경로의 유사도·무작위 선택과 발췌 범위를 계산하고 PreviewStore는 본문 없는 참조를 세션에 보관한다.
- `reading`은 `controller`, `dto`, `exception`, `repository`, `service/impl` 구조다. ReadingServiceImpl이 전체 독서 범위·최대 위치·version을 검증하고 ReadingRepository가 book_reading_sessions를 저장한다.
- `embedding`은 `config`, `dto`, `exception`, `service/impl` 구조다. EmbeddingServiceImpl이 전용 RestClient로 FastAPI만 호출한다. 프런트용 컨트롤러·사용자 세션 전달·DB 직접 접근은 없다.
- 요청 흐름: Controller → Service 인터페이스 → ServiceImpl → Repository → PostgreSQL. `UserServiceImpl`은 회원 조회·생성을, `GoogleOidcUserServiceImpl`은 검증된 구글 사용자와 내부 회원의 연결을 담당한다. Controller·인증 설정은 서비스 인터페이스를 주입받는다.
- 응답 DTO는 `dto` 패키지에 두며 회원 응답 변환은 `MeResponse.from(User)`에서 처리한다. 외부 도서 API 호출은 연동 코드로 분리한다.
- 입력 검증·인증은 요청 경계에서, 업무 검증과 트랜잭션은 Service에서 처리한다.

## 인증 방식과 현재 구현

- 구글 OIDC 로그인 후 서버 세션으로 인증을 유지하고 브라우저는 세션 쿠키를 전송한다. [Spring Security 공식 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
- Boot 4용 `spring-boot-starter-security-oauth2-client`와 환경변수 기반 구글 클라이언트 설정을 사용한다.
- 사용자 식별은 `oauth_provider`와 `oauth_subject` 조합을 사용한다. 이메일·비밀번호 로그인과 서비스용 JWT 발급은 초기 범위에 포함하지 않는다.
- 최초 인증 시 회원을 자동 생성하고 확인된 이메일을 필수 수집한다. `UserServiceImpl`은 READ COMMITTED 트랜잭션과 제공자·sub 대상 `ON CONFLICT DO NOTHING`을 사용해 동시 생성을 처리한다. 회원 정보 수정 시 JPA가 `updated_at`을 갱신한다.
- 서버 메모리 세션과 `JSESSIONID` 쿠키를 사용한다. 비활성 만료 기본값은 30분이며 재시작 시 세션이 소실된다. 로그인 세션 테이블과 외부 세션 저장소는 사용하지 않는다.
- 상태 변경 API에는 CSRF 보호를 적용한다. 공개 `GET /api/csrf`가 세션 토큰과 헤더 이름을 반환하며 로그인·로그아웃 후 재조회한다. 인증·CSRF 오류와 MVC 오류는 같은 JSON 구조를 사용한다.
- `application.yaml`은 공통 설정, `application-local.yaml`·`application-prod.yaml`은 환경별 쿠키 설정이다. `SPRING_PROFILES_ACTIVE`로 선택한다. DB·구글·프런트 값은 IntelliJ 실행 설정이나 배포 환경변수로 주입하며 `.env.example`은 값 없는 안내 파일이다.
- 세션 쿠키는 `HttpOnly=true`, 경로 `/`이며 로컬은 기본 `Secure=false`, 운영·프로필 미지정 시 `true`다. `SameSite` 기본값은 `lax`다. CORS는 명시한 출처에 자격 증명을 허용하고, 운영 도메인·프록시·브라우저 쿠키 동작은 R5에서 확인한다.
- JPA는 `ddl-auto=validate`, SQL 자동 초기화는 비활성화한다. 기존 DB를 애플리케이션 실행으로 자동 변경하지 않는다.

## Swagger 문서와 로컬 검증

- `local` 프로필에서 `/swagger-ui/index.html`과 `/v3/api-docs`를 로그인 없이 조회할 수 있다. 공통·`prod` 설정에서는 문서 생성을 비활성화하고 보안 설정에서도 문서 경로를 차단한다. `local`과 `prod`를 함께 지정해도 공개하지 않는다.
- 각 API에 목적·사전 조건·응답 설명을, DTO에 필드 설명을 작성한다. 내부 인증 객체와 CSRF 객체는 요청 입력란에서 숨긴다.
- `OpenApiConfig`는 구글 로그인 링크와 테스트 순서를 안내하고, Security 필터가 처리하는 `POST /api/logout`의 헤더·응답을 문서에 등록한다. 별도 로그아웃 Controller는 추가하지 않는다.
- 같은 브라우저에서 구글 로그인 → 본인 조회 → CSRF 토큰 조회 → `X-CSRF-TOKEN`을 넣어 로그아웃 → 본인 조회 `401`을 확인한다. 자세한 순서는 `product-specs/api.md`를 따른다.
- 패키지·서비스 정리를 포함한 R1 검증 완료는 사용자 보고를 기준으로 기록했다. 운영·프런트 통합 확인은 R5에서 수행한다.

## 데이터·페이지·추천 경계

- 초기 입력은 docs/shortB-15books-texts의 README·books.json, shortb_axes.csv, shortb_grid.csv다. 도서 본문은 에이전트가 직접 열거나 출력하지 않는다. 가져오기·검증은 원문을 출력하지 않는 프로그램으로 수행한다.
- README 기준 15편, 한국어 원문 6편·AI 한국어 전문 번역 9편이다. 판본·교열 전 안내·출처·이용 조건을 유지한다. 자동 구조 확인과 작품 완전성·권한 검수는 구분한다.
- pages 하나가 서비스 페이지 하나다. 입력 배열·chapters는 0-based, 표시·업무 API는 1-based. 긴 페이지의 내부 검색 구간도 원본 페이지/위치에 연결한다.
- 발췌는 검색/대비 근거를 포함하는 연속 min(요청 1~10쪽,작품 전체 쪽수) 범위다. 끝부분은 시작점을 조정하며 짧은 작품을 요청량 부족으로 제외하지 않는다.
- 첫 책과 대비 책의 실제 수는 서로 다를 수 있다. 전체 독서 세션은 선택한 책의 첫 페이지부터 시작하고 전체 책 기준 진행량을 저장한다. 발췌 분량은 자동 합산하지 않는다.

## Spring·FastAPI·사전 처리 책임

| 구성 요소 | 책임 |
| --- | --- |
| Spring | 세션 인증·DB 가져오기/조회·유사도/카드 점수 조합·균등 무작위·대비 검증·연속 페이지·전체 독서 기록 |
| FastAPI | GPU 모델 로딩·재사용·요청 프롬프트 임베딩·상태/모델 정보. DB/Google 세션 직접 접근 없음 |
| Python 스크립트 | 로컬 도서 구조 검증·기존 페이지의 검색 입력 변환, 본문/카드 사전 임베딩·원본 ID/해시/모델 정보 파일 출력 |
| 프런트 | 1~10쪽·필수 카드·선택 프롬프트 입력, 두 발췌 표시·전체 독서 선택·진행 UI. Spring만 호출 |

- 본문 구간은 document, 따뜻한/유쾌한/생각할 카드와 프롬프트는 query 역할이다. 같은 모델 설정과 호환 입력 지침을 사용한다.
- ShortsB-AI의 prepare_catalog.py는 FastAPI와 별도로 실행하는 콘텐츠 준비 CLI다. 현재 15개 작품·864페이지를 페이지별 JSONL로 변환하고 UTF-16 위치·출처 안내를 보존했다. 실제 벡터 생성·DB 반영은 별도이며 로컬 구현 완료 후 서버에 업로드한다.
- ANY는 임베딩하지 않는다. ANY 단독은 유효 작품별 균등 무작위, 카드만은 사전 벡터, 프롬프트가 있으면 온라인 벡터로 처리한다.
- CSV는 속성·후보 기준이다. 대비 축은 결·시선·어조·태도·결말·시대·배경 6개며, 결은 전체 정서적 질감으로 어조·결말과 구분한다. 기준/대상 작품·축별 관계를 구분하고 pending/approved/rejected 상태로 관리한다. 후보 1~3을 추천 점수로 변환하지 않는다. 첫 실제 발췌 범위와 양쪽 근거·승인 상태를 확인한 뒤 대비를 제공한다.
- 공개 모델은 KURE-v1을 사용하며 실제 앱의 GPU/Spring 통합·고정 revision은 추가 확인한다. 카드+프롬프트 기본 가중치는 0.4/0.6으로 승인됐다. 벡터는 사용자 승인으로 PostgreSQL double precision[]에 저장하고 초기 유사도 계산은 Spring에서 수행한다. 별도 벡터 DB·확장은 추가하지 않는다. 인터페이스 제안은 plans/fastapi-ai-handoff.md를 따른다.
- 환경변수·접속·자격 증명이 필요하면 해당 구현 전에 이름과 용도를 안내한다. Spring의 Google/DB 비밀값을 FastAPI로 전달하지 않는다.

## 도서·페이지·벡터 등록

- books에 외부 ID·자료 버전·쪽수·본문 해시·pageJoiner·본문 제외 메타데이터를 저장한다. 원문은 book_pages, 검색 위치는 book_search_segments로 분리한다. 현재 매핑은 페이지 하나에 검색 구간 하나다.
- embedding_models·book_segment_embeddings·card_embeddings에 불변 모델 설정과 double precision[] 벡터를 저장한다. 추천 서비스는 recommendation_configuration의 모델 기준과 일치하는 벡터를 사용해야 한다. 첫 추천의 코사인 유사도 전수 검색 코드를 작성했으며 실제 벡터/DB 실행은 미검증이다.
- importCatalog Gradle 명령은 --validate-only 또는 --apply를 명시해야 한다. 적용 시 기존 DB 환경변수만 읽고 Google·GPU·웹 서버는 시작하지 않는다.
- 전체 파일 검증·동일 데이터 재사용·변경 데이터 거절·트랜잭션 롤백을 적용한다. 전체 본문/카드 등록 후만 추천 모델 설정을 전환하며 권한은 자동 승인하지 않는다.
- 사용자 실행용 합성 파일 검사 8개를 작성했으나 실행하지 않았다. 등록 절차·제약·검증 방법은 [content-import.md](database/content-import.md)를 따른다.

## Spring 임베딩 연결 설정

- APP_EMBEDDING_BASE_URL 기본값은 로컬 SSH forwarding용 http://127.0.0.1:18001이다. 실제 연결 시 환경에 맞게 지정한다. APP_EMBEDDING_API_KEY가 있으면 X-API-Key로 전송한다.
- APP_EMBEDDING_CONNECT_TIMEOUT·APP_EMBEDDING_READ_TIMEOUT의 기본값은 2s·30s다. JDK HTTP 클라이언트를 사용하고 리다이렉트를 따라가지 않는다. 앱 시작 시 모델 상태를 조회하지 않으며 서비스 코드에서 자동 재시도하지 않는다.
- 사전 manifest에서 가져온 modelId·고정 revision·fingerprint·dimension·normalized를 기대 모델로 전달한다. 응답이 GPU 준비 상태인지, 단건 query ID·역할·벡터 길이·유한 값·L2 정규화가 맞는지 확인한다. 모델 설정 불일치 벡터는 반환하지 않는다.
- 연결 오류·처리 중·GPU/모델 불일치는 503, 잘못된 응답은 502, 입력 오류는 400으로 안전한 code/message를 반환한다. 외부 오류 본문·키·프롬프트를 로그나 응답으로 전달하지 않는다.
- 계약 검사 7개는 사용자 실행용으로 작성했으며 Codex는 실행하지 않았다. 첫 추천 API 코드를 작성했으며 실제 벡터 DB 반영·서버 통합은 후속 작업이다.

## 첫 추천·발췌 API

- POST /api/previews, GET /api/previews/{previewId}, GET /api/books/{bookId}를 작성했다. 로그인·POST CSRF·삭제 회원 검증을 적용하고 응답은 no-store다. 계약은 [api.md](product-specs/api.md)를 따른다.
- 승인된 원본 페이지가 모두 있는 작품만 제공한다. ANY 단독은 작품별 균등 선택·첫 페이지이며 모델을 조회하지 않는다. 다른 경로는 활성 모델/자료 버전에 맞고 전체 페이지 벡터가 있는 작품에서 코사인 유사도 최댓값을 찾는다.
- 카드+프롬프트는 기본 카드 40%·프롬프트 60%다. app.recommendation.prompt-weight/APP_RECOMMENDATION_PROMPT_WEIGHT로 프롬프트 비중을 설정한다. 기본값 0.6, 유한한 0~1이며 카드 비중은 1-설정값이다.
- RecommendationRepository의 반복 읽기 스냅샷을 완료한 뒤 GPU 요청을 보낸다. HTTP 대기 중 DB 트랜잭션을 유지하지 않는다. 반환 페이지는 BookService의 별도 스냅샷에서 승인·본문 버전·쪽수·페이지 해시를 확인한다.
- 세션에는 소유자·입력·본문 해시·범위 등 참조만 최대 10개 저장한다. 초과 시 생성 순서로 제거하며 재조회는 GPU 없이 DB에서 본문을 조회한다. 변경 본문 409·없는/다른 소유자 발췌 404·미승인 작품 404다.
- 추천 이유는 처리한 경로의 고정 안내다. 생성형 본문 해석·대비·전체 독서 API는 추가하지 않았다.
- 사용자 요청으로 새 테스트 코드는 작성하지 않았다. Spring 빌드·검사·DB 적용·git은 실행하지 않았으며 R2를 완료 처리하지 않는다.

## 스키마와 계약 영향

- 스키마 기준은 docs/database/schema.sql이다. 초기 생성용이며 기존 DB 갱신은 별도 변경 SQL이 필요하다. erd.pgerd는 자동 동기화되지 않는다.
- books의 topics·moods·edition·language·source_url·rights_evidence와 book_relations의 양쪽 근거 구간 FK가 이미 존재한다.
- user_preferences는 과거 취향 배열이다. 기존 reading_sessions는 선택 구간 중심 과거 기록이며 신규 전체 독서에 사용하지 않는다. 전체 독서는 book_reading_sessions에 별도 저장한다.
- 페이지·외부 ID·원본 출처/번역 메타데이터·검색 구간·모델·본문/카드 벡터·단일 추천 설정의 SQL과 수동 등록 코드를 작성했다. 기존 DB용 변경 SQL은 database/migrations/001-content-embeddings.sql이다. 실제 DB 적용은 미실행이며 기존 독서·대비 테이블은 보존하고 R3는 별도 book_reading_sessions를 사용한다. R4는 별도 book_contrast_relations·003 변경 SQL을 작성했으며 실제 검수 등록은 대기 중이다. 발췌 DB 테이블은 추가하지 않는다.
- recommendation_logs의 성과 측정 확장은 보류한다. 공유·공유 카드는 무기한 보류이며 새 API·완료 조건에 추가하지 않는다.
- openAPI.md는 사전 공급원 후보 자료로 수정 보류다. 요청마다 외부 전체 도서를 수집하는 기능은 이번 범위가 아니다.

## 후속 확정: 상태 보관과 원자적 저장

- 발췌 ID·입력·작품/범위는 서버 메모리 세션에 임시 보관하고 본문은 DB에서 조회한다. 별도 발췌 DB 테이블은 만들지 않는다. 세션 소실 시 새 추천을 요청하며 전체 독서 DB 기록과 구분한다.
- ANY 단독은 책별 균등 선택 후 첫 페이지 발췌다. 근거 없는 대비는 생략하되 첫 책 전체 독서 시작은 허용한다.
- 회원·책마다 미완료 전체 독서 세션 하나를 원자적으로 생성/재사용한다. 완료 후 재선택은 새 세션이며 다른 책은 별도 미완료 세션을 가질 수 있다.
- 현재 위치·최대 도달 위치·저장 버전을 구분한다. 최신 상태의 뒤로 이동은 현재 위치만 바꾸고 최대 위치 기반 진행률은 유지한다.
- 진행·중단·완료는 저장 버전 일치 확인과 갱신을 원자적으로 수행한다. 불일치 409에 최신 위치·상태·버전을 반환하며 거절된 요청은 최대 위치도 바꾸지 않는다. 프런트는 과거 요청 버전만 교체한 자동 재전송을 하지 않는다.
- R3에서 전체 독서 스키마·시작/조회/페이지/진행/완료 코드를 작성했다. 기존 DB용은 migrations/002-whole-book-reading.sql이며 DB 적용·실행 검증은 미완료다. 대비는 R4 별도 테이블·등록/API 코드를 작성했으나 실제 적용은 미실행이다.


## 전체 독서 저장·동시성

- 회원·책별 미완료 전체 독서는 부분 유일 인덱스로 하나만 허용한다. 신규 시작은 첫 페이지·위치 0이며 완료 후 재선택은 새 기록이다. 미완료 재사용은 상태/위치를 변경하지 않는다.
- 쓰기 메서드는 READ COMMITTED 트랜잭션에서 회원 행 FOR UPDATE를 먼저 잠근다. 같은 회원의 동시 시작·진행·완료를 직렬화하고 기존 세션은 소유자 조건으로 행을 잠근다. 도서 FOR SHARE로 처리 중 승인/버전 메타데이터 변경을 막는다. [PostgreSQL 행 잠금](https://www.postgresql.org/docs/current/explicit-locking.html)
- version 일치 확인과 조건부 UPDATE를 같은 트랜잭션에 둔다. 충돌은 409 {code,message,current}로 반환하고 거절된 요청은 최대 위치도 바꾸지 않는다. DB 저장 실패는 롤백한다.
- 본문 해시·쪽수·UTF-16 전체 길이를 시작 시 저장한다. 페이지 길이만으로 누적 위치를 계산하고 실제 조회/저장 대상 페이지의 본문 해시·UTF-16 경계를 확인한다. 전체 본문을 한 번에 로딩하지 않는다.
- 진행률은 페이지 구분자 제외 최대 누적 위치/전체 본문 길이×100이며 소수 둘째 자리까지 내림한다. 현재 위치와 최대 위치를 분리하고 100% 도달과 completed를 구분한다.
- 최근 저장 순서에 잠금 대기 전 트랜잭션 시각이 반영되지 않도록 INSERT/UPDATE에 statement_timestamp()를 사용한다. [PostgreSQL 현재 시각](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT)
- 상세 API·완료/재전송 정책은 product-specs/api.md, 작업 현황은 plans/active/r3-whole-book-reading.md를 따른다. 테스트 코드 작성·빌드·실행·DB 적용은 하지 않았다.


## 대비 근거와 추천

- 기존 book_relations와 로그는 보존한다. book_contrast_relations는 source/target/6개 축으로 관계를 식별하고 양쪽 본문 해시·1-based 근거 범위/구절·사람 설명·검수자·상태를 저장한다.
- scripts/prepare_contrast_reviews.py로 CSV 45개 후보의 pending 양식을 생성했다. 본문/구절을 읽거나 출력하지 않았고 검수 승인은 하지 않았다. 자료 구조/실행은 [contrast-import.md](database/contrast-import.md)를 따른다.
- importContrasts는 웹 앱과 분리된 수동 명령이다. validate-only도 DB를 조회하며 쓰지 않는다. apply는 전체 파일을 트랜잭션으로 등록한다. pending 재등록은 기존 승인/제외를 유지하고 승인에는 양쪽 권한·해시·실제 구절 대조가 필요하다.
- ContrastServiceImpl은 첫 발췌의 소유자 확인 후 동일 읽기 스냅샷에서 원본/관계/모델/본문 벡터를 조회한다. 근거 범위가 두 실제 발췌에 포함되는 승인 관계만 사용한다. 생성형 대비·낮은 유사도 기반 반대 주장·추가 GPU 호출은 없다.
- 첫 발췌 평균과 대상 근거를 담는 연속 창 평균을 정규화한 코사인 점수로 정렬한다. VectorSimilarity를 첫 추천과 공유한다. 첫 모델을 유지하며 ANY 단독이면 활성 모델을 사용한다.
- 후보/범위 없음은 200 available=false, 준비 부족은 503이다. 두 번째 previewId나 세션 항목은 생성하지 않고 sourcePreviewId로 연결한다. 응답의 양쪽 bookId로 기존 R3 전체 독서를 시작한다.
- 003 변경 SQL·검수 파일 반영·실제 API 실행은 미완료다. 테스트 코드는 추가하지 않았으며 기존 검사 파일은 유지한다.
