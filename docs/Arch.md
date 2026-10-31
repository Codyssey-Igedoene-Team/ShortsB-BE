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

`com.shortsb` 아래에 R1의 `auth`·`user`·`common` 구현이 있다. 사용자가 로컬 서버 기동·실제 구글 로그인·세션을 통한 본인 정보 조회 성공을 확인했다. 로그아웃·실패 시나리오와 추가한 자동 테스트는 확인 대기 중이며 콘텐츠·독서·추천 API는 후속 단계에서 구현한다.

## 구현 구조안

- 단일 Spring 애플리케이션으로 구성한다.
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
- 기존 구글 로그인·본인 조회의 사용자 확인은 패키지 정리 전 결과다. 이번 구조 변경 후 서버 기동·Swagger 표시·인증 흐름 재확인은 대기 중이다.

## 데이터와 외부 연동

- 저장소 루트 기준 스키마 위치는 `docs/database/schema.sql`이다. `docs/database/erd.pgerd`는 참고용이며 스키마 변경과 자동 동기화되지 않는다.
- 사용자·취향·도서·본문 구간·독서 세션·대비 관계·추천 기록을 PostgreSQL에 저장한다.
- 콘텐츠와 추천 관계는 사전 확보·검수한 데이터를 사용한다. 최초 추천은 아래 GPU 임베딩 방식으로 처음부터 구현하며, 구체적인 모델·저장·연동 방식은 상세계획에서 정한다.
- 외부 공급원은 `openAPI.md`의 후보 중 확정한다. 외부 장애 시 확보된 콘텐츠를 사용하고 검증되지 않은 후보를 반환하지 않는다.
- 이 SQL은 초기 생성용 스키마이며 기존 DB에 대한 변경 적용은 별도 마이그레이션이 필요하다.

## 최초 추천의 GPU 임베딩 구현 방향

- 최초 추천 개발부터 제공받은 GPU에서 사전학습된 공개 한국어 임베딩 모델을 직접 실행한다. 직접 학습·파인튜닝은 요구하지 않는다.
- 책 소개 또는 검토한 본문 구간을 사전 임베딩하고, 사용자 관심사를 임베딩해 유사도로 후보를 검색한다. 선택 분량 조건을 적용하며, 가용 시간·읽기 기록 반영의 상세 범위는 별도 결정한다.
- GPU 실행 환경과 Spring 백엔드 연동, 벡터 저장·검색 방식, 모델·벡터 버전 관리, 실행 실패 시 대체 동작은 상세계획에서 결정한다. 별도 추론 서버나 벡터 DB 도입은 아직 확정하지 않는다.
- 실제 GPU 사양·이용 가능 기간, 모델 라이선스·콘텐츠 언어 적합성, 사전 처리 시간·요청 지연을 최초 추천 구현 전에 확인한다. 대체 추천만으로 임베딩 구현을 완료 처리하지 않는다.
- 기존 분량 선택과 검수된 대비 관계·근거 정책을 유지한다. 세부 검토 사항은 `product-specs/recommendation.md`와 `planning-review.md`의 12번을 참고한다.
