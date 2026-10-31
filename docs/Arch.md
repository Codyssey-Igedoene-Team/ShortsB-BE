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

## 인증 방식 및 구현 예정 사항

- 구글 OIDC 로그인 후 서버 세션으로 인증을 유지하고 브라우저는 세션 쿠키를 전송한다. [Spring Security 공식 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)
- OAuth2 Client 의존성과 구글 클라이언트 설정은 구현 시 추가해야 한다. 현재 의존성에는 OAuth2 Client가 없다.
- 사용자 식별은 `oauth_provider`와 `oauth_subject` 조합을 사용한다. 이메일·비밀번호 로그인과 서비스용 JWT 발급은 초기 범위에 포함하지 않는다.
- 세션 만료 시간·저장소는 미정이다. 기존 `user_sessions`는 Spring 기본 세션 저장소와 자동 연동되지 않는다.
- 상태 변경 API에는 CSRF 보호를 적용한다. 배포 도메인 확정 후 CORS·쿠키의 `HttpOnly`·`Secure`·`SameSite` 정책을 설정한다.

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
