# API 명세 초안

아직 구현되지 않은 핵심 API의 계약안이다. 인증 방식은 구글 소셜 로그인·서버 세션·세션 쿠키로 확정하며, 상세 구현 계약은 아래 미정 항목을 따른다.

## 공통 규칙

- 업무 API는 `/api`를 사용하고 로그인한 사용자 본인 데이터만 처리한다. 사용자 ID는 세션에서 가져온다.
- JSON 필드는 camelCase, 시간은 ISO 8601 UTC로 통일한다. `bigint` ID는 JavaScript 정밀도 손실을 막기 위해 JSON 문자열로 반환한다.
- 단건 성공은 객체, 목록은 `{ "items": [] }`, 오류는 `{ "code": "INVALID_REQUEST", "message": "요청값을 확인해주세요." }` 형식이다.
- 상태 코드는 조회·수정 `200`, 생성 `201`, 로그아웃 `204`를 사용한다. 리다이렉트는 별도 표기한다.
- `400` 잘못된 입력, `401` 로그인 필요, `403` 권한·CSRF 검증 실패, `404` 대상 없음, `409` 상태 충돌을 사용한다.
- 본인 소유가 아닌 독서 세션은 `404`로 처리한다. 상태 변경 요청에는 CSRF 토큰이 필요하며, 토큰 제공 방식은 구현 전에 확정한다.

## 인증

| 메서드·경로 | 역할 | 결과 |
| --- | --- | --- |
| `GET /oauth2/authorization/google` | 구글 로그인 시작 | 구글 인증 화면으로 `302` |
| `GET /login/oauth2/code/google` | 구글 인증 콜백 | Spring Security가 인증 처리 후 서버 세션 생성·클라이언트로 `302` |
| `GET /api/me` | 로그인 사용자 조회 | `{ id, email, nickname }` |
| `POST /api/logout` | 로그아웃 | 서버 세션 무효화·쿠키 제거 후 `204` |

로그인 시작·콜백만 비로그인 접근을 허용한다. 콜백 성공·실패 이동 주소와 CSRF 토큰 조회 경로는 미정이다. 최초 로그인 시 회원 자동 생성은 제안 사항이다.

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
