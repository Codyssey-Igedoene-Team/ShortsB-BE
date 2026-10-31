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

현재 코드는 `com.shortsb`의 시작 클래스만 있다. DB 연결·인증·업무 API는 아직 구현되지 않았다.

## 구현 구조안

- 단일 Spring 애플리케이션으로 구성한다.
- 도메인별 `auth`, `user`, `book`, `reading`, `recommendation` 패키지를 둔다.
- 각 도메인에 필요한 Controller·Service·Repository·Entity·DTO만 추가한다.
- 요청 흐름: Controller → Service → Repository → PostgreSQL. 외부 도서 API 호출은 연동 코드로 분리한다.
- 입력 검증·인증은 요청 경계에서, 업무 검증과 트랜잭션은 Service에서 처리한다.

## 인증 구성안

- 구글 OIDC 로그인 후 서버 세션으로 인증을 유지하고 브라우저는 세션 쿠키를 전송한다. [Spring Security 공식 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
- OAuth2 Client 의존성과 구글 클라이언트 설정은 구현 시 추가해야 한다. 현재 의존성에는 OAuth2 Client가 없다.
- 사용자 식별은 `oauth_provider`와 `oauth_subject` 조합을 사용한다. 이메일·비밀번호 로그인과 서비스용 JWT 발급은 초기 범위에 포함하지 않는다.
- 세션 만료 시간·저장소는 미정이다. 기존 `user_sessions`는 Spring 기본 세션 저장소와 자동 연동되지 않는다.
- 상태 변경 API에는 CSRF 보호를 적용한다. 배포 도메인 확정 후 CORS·쿠키의 `HttpOnly`·`Secure`·`SameSite` 정책을 설정한다.

## 데이터와 외부 연동

- 스키마 기준은 `database/schema.sql`이다. `database/erd.pgerd`는 참고용이며 스키마 변경과 자동 동기화되지 않는다.
- 사용자·취향·도서·본문 구간·독서 세션·대비 관계·추천 기록을 PostgreSQL에 저장한다.
- 콘텐츠와 추천 관계는 사전 확보·검수한 데이터를 조회하는 흐름으로 구성한다. AI 모델 선택은 보류한다.
- 외부 공급원은 `openAPI.md`의 후보 중 확정한다. 외부 장애 시 확보된 콘텐츠를 사용하고 검증되지 않은 후보를 반환하지 않는다.
- 이 SQL은 초기 생성용 스키마이며 기존 DB에 대한 변경 적용은 별도 마이그레이션이 필요하다.
