# 백엔드 구조

## 현재 확인된 구성

| 항목 | 구성 |
| --- | --- |
| 언어·서버 | Java 21, Spring Boot 4.1.2, Spring MVC |
| 데이터 접근 | Spring Data JPA, PostgreSQL 드라이버 |
| 보안·검증 | Spring Security, Validation |
| 외부 HTTP 연동 | RestClient 관련 의존성 |
| API 문서 | springdoc OpenAPI UI 3.2.0 |
| 연동 클라이언트 | React |

`com.shortsb` 아래에 R1의 `auth`·`user`·`common` 구현이 있다. 사용자가 R1 전체 검증 완료를 보고하여 인증 단계를 완료 처리했다. 개별 실행 로그는 별도 수집하지 않았으며 Codex는 빌드·테스트를 실행하지 않았다. 현재 R2 상세계획을 작성했으며 콘텐츠·독서·추천 API는 아직 구현 전이다.

상위 `ShortsB-AI`에는 KURE-v1 공통 encoder·FastAPI health/embeddings·JSONL 사전 생성 도구를 작성했다. 모델 없는 Python/HTTP 계약 검사 10개는 통과했으며, 이는 Spring 빌드·테스트 실행과 별개다. 사용자 GPU의 합성 문장 임베딩은 확인했지만 새 앱의 GPU 기동·Spring 연동·실제 데이터 처리는 미검증이다.

## 구현 구조안

- Spring 업무 애플리케이션과 FastAPI 임베딩 프로세스로 역할을 분리한다. 사전 벡터 생성은 같은 encoder를 사용하는 Python 스크립트로 수행한다. FastAPI 최소 코드는 별도 ShortsB-AI에 작성했고 실제 서버 기동·연동 확인은 남아 있다.
- 도메인별 `auth`, `user`, `book`, `reading`, `recommendation` 패키지를 둔다.
- 도메인 내부를 역할별 하위 패키지로 나눈다. 현재 `auth`는 `config`, `controller`, `dto`, `security`, `service/impl`, `user`는 `controller`, `dto`, `entity`, `repository`, `service/impl`, `common`은 `config`, `dto`, `exception`으로 구성한다.
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
- 모델·GPU 연결·가중치·벡터 저장은 후속 논의한다. 별도 벡터 DB를 필수 전제로 추가하지 않는다. 인터페이스 제안은 plans/fastapi-ai-handoff.md를 따른다.
- 환경변수·접속·자격 증명이 필요하면 해당 구현 전에 이름과 용도를 안내한다. Spring의 Google/DB 비밀값을 FastAPI로 전달하지 않는다.

## 스키마와 계약 영향

- 스키마 기준은 docs/database/schema.sql이다. 초기 생성용이며 기존 DB 갱신은 별도 변경 SQL이 필요하다. erd.pgerd는 자동 동기화되지 않는다.
- books의 topics·moods·edition·language·source_url·rights_evidence와 book_relations의 양쪽 근거 구간 FK가 이미 존재한다.
- user_preferences의 과거 취향 배열은 새 필수 카드/선택 프롬프트 계약과 다르다. reading_sessions는 선택 구간 중심이며 새 전체 독서 기준과 대조해야 한다.
- 페이지/내부 검색 구간·외부 자료 ID·AI 번역 안내·벡터·발췌 참조, CSV의 6개 대비 축을 기존 4종 relation_type에 축소하지 않고 수용하는 스키마 반영은 후속 작업이다. 테이블을 일괄 삭제하거나 DB가 이미 변경됐다고 간주하지 않는다.
- recommendation_logs의 성과 측정 확장은 보류한다. 공유·공유 카드는 무기한 보류이며 새 API·완료 조건에 추가하지 않는다.
- openAPI.md는 사전 공급원 후보 자료로 수정 보류다. 요청마다 외부 전체 도서를 수집하는 기능은 이번 범위가 아니다.

## 후속 확정: 상태 보관과 원자적 저장

- 발췌 ID·입력·작품/범위는 서버 메모리 세션에 임시 보관하고 본문은 DB에서 조회한다. 별도 발췌 DB 테이블은 만들지 않는다. 세션 소실 시 새 추천을 요청하며 전체 독서 DB 기록과 구분한다.
- ANY 단독은 책별 균등 선택 후 첫 페이지 발췌다. 근거 없는 대비는 생략하되 첫 책 전체 독서 시작은 허용한다.
- 회원·책마다 미완료 전체 독서 세션 하나를 원자적으로 생성/재사용한다. 완료 후 재선택은 새 세션이며 다른 책은 별도 미완료 세션을 가질 수 있다.
- 현재 위치·최대 도달 위치·저장 버전을 구분한다. 최신 상태의 뒤로 이동은 현재 위치만 바꾸고 최대 위치 기반 진행률은 유지한다.
- 진행·중단·완료는 저장 버전 일치 확인과 갱신을 원자적으로 수행한다. 불일치 409에 최신 위치·상태·버전을 반환하며 거절된 요청은 최대 위치도 바꾸지 않는다. 프런트는 과거 요청 버전만 교체한 자동 재전송을 하지 않는다.
- 원자적 생성·저장 검증, 최대 위치·버전 필드와 제약을 현재 스키마에 반영하는 작업은 후속 구현이다. 이번 문서 수정에서 SQL·코드를 변경하지 않는다.
