# API 명세 초안

R1의 인증·본인 조회·CSRF API는 코드가 작성됐으며, 사용자가 로컬 서버 기동·실제 구글 로그인·본인 정보 조회 성공을 확인했다. 자동 테스트·로그아웃 등 나머지 검증은 대기 중이다. 취향·도서·독서·추천 API는 후속 단계의 계약 초안이다.

## 공통 규칙

- 업무 API는 `/api`를 사용하고 로그인한 사용자 본인 데이터만 처리한다. 사용자 ID는 세션에서 가져온다.
- JSON 필드는 camelCase, 시간은 ISO 8601 UTC로 통일한다. `bigint` ID는 JavaScript 정밀도 손실을 막기 위해 JSON 문자열로 반환한다.
- 단건 성공은 객체, 목록은 `{ "items": [] }`, 오류는 `{ "code": "INVALID_REQUEST", "message": "요청값을 확인해주세요." }` 형식이다.
- 상태 코드는 조회·수정 `200`, 생성 `201`, 로그아웃 `204`를 사용한다. 리다이렉트는 별도 표기한다.
- `400` 잘못된 입력, `401` 로그인 필요, `403` 권한·CSRF 검증 실패, `404` 대상 없음, `405` 지원하지 않는 메서드, `409` 상태 충돌, `415` 지원하지 않는 콘텐츠 형식, `500` 서버 오류를 사용한다.
- 본인 소유가 아닌 독서 세션은 후속 구현에서 `404`로 처리한다. 상태 변경 요청에는 `GET /api/csrf`에서 받은 CSRF 토큰이 필요하다.

## 인증

| 메서드·경로 | 역할 | 결과 |
| --- | --- | --- |
| `GET /oauth2/authorization/google` | 구글 로그인 시작 | 구글 인증 화면으로 `302` |
| `GET /login/oauth2/code/google` | 구글 인증 콜백 | Spring Security가 인증 처리 후 서버 세션 생성·클라이언트로 `302` |
| `GET /api/csrf` | 현재 세션의 CSRF 토큰 조회 | `{ token, headerName }`, `200`, `Cache-Control: no-store` |
| `GET /api/me` | 로그인 사용자 조회 | `{ id, email, nickname }` |
| `POST /api/logout` | 로그아웃 | 서버 세션 무효화·쿠키 제거 후 `204` |

- 로그인 시작·콜백·CSRF 조회는 비로그인 접근을 허용한다. 프리플라이트는 허용 출처에 대해 인증 전에 처리한다. 그 밖의 업무 요청은 로그인해야 한다.
- 프런트는 페이지 이동으로 구글 로그인을 시작한다. 구글의 검증된 `sub`와 제공자로 회원을 식별하고, 최초 인증 시 회원을 자동 생성한다. 이메일은 필수이며 `email_verified=true`를 확인한다.
- 로그인 성공은 `APP_LOGIN_SUCCESS_URL`, 실패는 `APP_LOGIN_FAILURE_URL`에 지정한 고정 URL로 이동한다. 구글 콘솔에 등록한 콜백 URL은 `GOOGLE_REDIRECT_URI`와 일치해야 한다.
- `GET /api/me`는 세션의 내부 회원 ID로 조회하며 요청의 사용자 ID를 사용하지 않는다. 응답의 `id`는 문자열이고, 닉네임은 없으면 `null`이다. 비로그인·삭제된 회원은 JSON `401`을 반환한다.
- 세션은 서버 메모리에 보관하고 `JSESSIONID` 쿠키로 유지한다. 기본 비활성 만료 시간은 30분이며 서버 재시작 후에는 재로그인이 필요하다.
- API 호출에는 `credentials: 'include'`에 해당하는 옵션을 사용한다. CORS 허용 출처는 `APP_CORS_ALLOWED_ORIGINS`로 지정한다. 브라우저의 CORS 차단 응답은 공통 JSON 오류 형식을 보장하지 않는다.

### CSRF와 로그아웃

1. `GET /api/csrf`를 쿠키 포함 요청으로 호출한다. 필요한 경우 익명 세션과 토큰이 생성된다.
2. 응답의 `headerName`(현재 `X-CSRF-TOKEN`)에 `token` 값을 넣어 POST·PUT·PATCH·DELETE 요청을 보낸다.
3. 구글 로그인 성공 후 새 토큰을 조회한다. 로그인 전 토큰을 계속 사용하지 않는다.
4. `POST /api/logout`에 유효한 세션 쿠키와 토큰을 보내면 세션을 무효화하고 쿠키를 제거한 뒤 `204`를 반환한다.
5. 로그아웃 후 다음 상태 변경 요청 전에 토큰을 다시 조회한다.

- CSRF 토큰은 서버 세션에 저장하며, 조회 API가 반환하는 토큰을 그대로 사용한다. 세션 쿠키를 JavaScript에서 읽을 필요는 없다.
- 비로그인 `GET /api/me`는 `401 / AUTHENTICATION_REQUIRED`, 로그인 상태의 CSRF 누락·위조는 `403 / CSRF_INVALID`다. 비로그인 변경 요청도 CSRF 필터가 먼저 거절하면 `403`일 수 있다.
- 유효한 CSRF 토큰만 있고 로그인하지 않은 로그아웃 요청은 `401`이다. 일반 권한 오류는 `403 / ACCESS_DENIED`이며 내부 오류는 `500 / INTERNAL_ERROR`다.
- 로컬 `local` 프로필은 기본 `Secure=false`, 운영 `prod`와 프로필 미지정 시 기본 `Secure=true`다. `SameSite` 기본값은 `lax`이며 운영 도메인 확정 후 브라우저에서 확인한다.

### Swagger에서 로컬 인증 테스트

`local` 프로필로 실행한 서버의 `http://localhost:8080/swagger-ui/index.html`에 접속한다. 문서 UI와 `/v3/api-docs`는 로컬에서 로그인 없이 조회할 수 있으며, 운영·프로필 미지정 시에는 비활성화하고 문서 경로도 차단한다. `local`·`prod` 동시 활성화 시에도 공개하지 않는다. 업무 API의 인증 조건은 위 계약을 따른다.

1. 비로그인 상태에서 **회원 → 로그인한 내 정보 조회 → Try it out → Execute**를 눌러 JSON `401`을 확인한다.
2. 문서 상단의 **구글 로그인 시작** 링크를 새 탭으로 열고 로그인한다. 성공 URL의 프런트가 실행 중이 아니어도 같은 브라우저의 Swagger 탭으로 돌아와 확인할 수 있다.
3. 본인 조회를 실행해 `200`과 회원 정보를 확인한다. 같은 서버로 보내는 요청에는 브라우저가 세션 쿠키를 전달하므로 별도 인증값을 입력하지 않는다.
4. **인증 → CSRF 토큰 조회 → Try it out → Execute** 후 실제 응답의 `token` 값을 복사한다. Example Value의 값은 사용하지 않는다.
5. **인증 → 로그아웃 → Try it out**의 `X-CSRF-TOKEN`에 복사한 값을 넣고 Execute한다. `204`이며 응답 본문이 없어야 한다.
6. 본인 조회를 다시 실행해 `401`을 확인한다. 재로그인 시 회원 ID가 유지되는지 확인하고 CSRF 토큰은 다시 조회한다.

로그아웃 문서는 Security 필터가 처리하는 경로를 OpenAPI에 등록한 것이다. 구글 로그인은 페이지 이동으로 진행하며, Swagger의 일반 API 실행으로 구글 인증 화면을 호출하지 않는다. 클라이언트 비밀값·세션 쿠키를 입력할 필요는 없다. 이번 문서·패키지 정리 후 위 순서의 재검증은 대기 중이다.

## 취향과 도서

| 메서드·경로 | 요청 | 성공 응답 |
| --- | --- | --- |
| `GET /api/me/preferences` | 없음 | `{ favoriteGenres, interests, preferredMoods }` |
| `PUT /api/me/preferences` | 세 항목 모두 문자열 배열로 전달 | 저장한 취향 객체 |
| `GET /api/recommendations` | query: `targetSegments` | `{ items: [{ bookId, title, author, startSegmentNo, targetSegments, reason }] }` |
| `GET /api/books/{bookId}` | 도서 ID | `{ id, title, author, description, coverUrl, sourceUrl, contentRights }` |

취향 미저장 시 각 배열은 비어 있다. 입력 가능한 취향 값은 미정이다. 추천 후보가 없으면 빈 목록을 반환한다.

## 독서

분량은 화면에서 1~10장 중 원하는 정수 분량으로 자유롭게 선택한다. 아래 `targetSegments`는 내부 구간 수 기준의 잠정 필드이며, 1장과 구간의 대응을 확정한 뒤 최종 요청 계약을 정한다. 사용자 선택값의 1~10 제한을 내부 구간 수에 그대로 적용하는 것으로 확정한 것은 아니다.

| 메서드·경로 | 요청 | 성공 응답 |
| --- | --- | --- |
| `POST /api/reading-sessions` | body: `bookId`, `startSegmentNo`, `targetSegments` | 생성한 독서 세션, `201` |
| `GET /api/reading-sessions/current` | 없음 | 최근 갱신한 미완료 세션, 없으면 `null` |
| `GET /api/reading-sessions/{sessionId}` | 세션 ID | 독서 세션 |
| `GET /api/reading-sessions/{sessionId}/segments/{segmentNo}` | 해당 세션 범위 내 구간 번호 | `{ segmentNo, title, content, sourceUrl }` |
| `PATCH /api/reading-sessions/{sessionId}/progress` | body: `lastSegmentNo`, `lastPosition`, `status` | 갱신한 독서 세션 |
| `POST /api/reading-sessions/{sessionId}/complete` | 없음 | 완료한 독서 세션 |

- 세션 응답: `{ id, bookId, startSegmentNo, targetSegments, completedSegments, lastSegmentNo, lastPosition, status, completedAt }`.
- 구간 번호·목표 분량은 양의 정수다. 서버는 본문 존재 여부와 구간 범위를 검증한다.
- 위치 단위안: `lastPosition`은 구간 본문 내 UTF-16 코드 단위 오프셋이며 0부터 시작한다. 범위는 본문 길이 이내다.
- 진행 저장의 `status`는 `reading` 또는 `paused`다. `completedSegments`는 클라이언트 값을 신뢰하지 않고 서버가 저장 위치로 계산한다.
- 앞부분을 다시 읽을 때 현재 위치와 이미 읽은 분량을 구분하는 계산 규칙, 여러 기기·탭 및 지연 재전송 요청의 충돌 처리 계약은 미정이다. 현재 응답 필드만으로 해당 정책이 확정된 것은 아니다.
- 완료 API는 목표 마지막 구간의 끝에 도달했는지 검증한다. 미도달은 `409`, 이미 완료한 세션은 기존 완료 결과를 반환한다.

## 대비 추천

| 메서드·경로 | 요청 | 성공 응답 |
| --- | --- | --- |
| `GET /api/reading-sessions/{sessionId}/recommendations` | 완료한 세션 ID | `{ items: [{ relationId, bookId, title, relationType, commonTopic, reason }] }` |

미완료 세션은 `409`, 승인된 대비 관계가 없으면 빈 목록을 반환한다. 노출 기록은 기존 초안상 서버에서 처리하며, 클릭·다음 읽기 시작 기록의 전달 계약은 미정이다. 성과 측정 범위와 이벤트 수집 계약의 확정·확장은 보류한다.

### 다음 읽기로의 연결: 미결정 계약

- 현재 대비 추천 응답에는 `bookId`가 있지만 독서 세션 생성에 필요한 `startSegmentNo`와 `targetSegments`는 없다.
- 추천한 책의 처음부터 읽을지, 대비 근거가 있는 구간부터 읽을지 정해야 한다. 근거가 중간에 있는데 처음부터 제공하면 선택 분량 안에 대비 내용이 포함되지 않을 수 있다.
- 이전 선택 분량을 유지할지, 1~10장 중 다시 선택할지 정해야 한다. 결정한 분량을 제공할 수 있는 본문이 존재하는지도 검증해야 한다.
- 시작 구간·분량을 추천 응답에 포함할지, 추천 선택 후 서버에서 결정할지는 미정이다. 관련 필드나 새 API는 아직 확정하지 않는다.

완료 화면의 공유하기는 PRD에 따라 P0이며, 공유 카드는 P1이다. 기본 공유의 대상·공개 범위·구현 방식이 미정이므로 전용 공유 API 필요 여부와 계약도 보류한다. 연속 독서 일수·주간 독서 칸은 현재 PRD의 MVP 필수 범위에 포함하지 않으며, 관련 API를 추가하지 않는다.
