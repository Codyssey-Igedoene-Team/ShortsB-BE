# API 명세 초안

R1의 인증·본인 조회·CSRF·로그아웃 API는 사용자의 전체 검증 완료 보고를 기준으로 완료 처리했다. 개별 실행 로그는 별도로 수집하지 않았으며 운영·프런트 통합 검증은 R5에서 수행한다. 첫 추천 생성·발췌 재조회·도서 정보 API 코드를 작성했다. Spring 실행·DB/GPU 통합은 미검증이다. 대비·전체 독서 API는 아직 구현 전 제안 계약이다.

## 공통 규칙

- 업무 API는 `/api`를 사용하고 로그인한 사용자 본인 데이터만 처리한다. 사용자 ID는 세션에서 가져온다.
- JSON 필드는 camelCase, 시간은 ISO 8601 UTC로 통일한다. `bigint` ID는 JavaScript 정밀도 손실을 막기 위해 JSON 문자열로 반환한다.
- 단건 성공은 객체, 목록은 `{ "items": [] }`, 오류는 `{ "code": "INVALID_REQUEST", "message": "요청값을 확인해주세요." }` 형식이다.
- 상태 코드는 조회·수정 `200`, 생성 `201`, 로그아웃 `204`를 사용한다. 리다이렉트는 별도 표기한다.
- `400` 잘못된 입력, `401` 로그인 필요, `403` 권한·CSRF 검증 실패, `404` 대상 없음, `405` 지원하지 않는 메서드, `409` 상태 충돌, `415` 지원하지 않는 콘텐츠 형식, `500` 서버 오류를 사용한다.
- 본인 소유가 아닌 독서 세션은 후속 구현에서 `404`로 처리한다. 상태 변경 요청에는 `GET /api/csrf`에서 받은 CSRF 토큰이 필요하다.

## 내부 임베딩 연결

Spring의 EmbeddingService/EmbeddingServiceImpl이 FastAPI GET /health·POST /embeddings를 호출하는 코드를 작성했다. 프런트가 직접 호출하는 API는 추가하지 않았다. 실제 네트워크·GPU·추천 업무 API 연결은 아직 미검증이다.

- 자유 프롬프트를 query 역할의 단건 요청으로 전송한다. 카드·본문 사전 임베딩이나 ANY 단독 요청을 이 서비스에서 새로 생성하지 않는다.
- 사전 manifest에서 가져온 modelId·revision·fingerprint·dimension·normalized를 기대 모델로 전달받는다. 프런트 요청값으로 모델 설정을 선택하지 않는다.
- 응답의 모델 설정·GPU 준비 상태·요청 ID·역할·개수·벡터 차원·유한 실수·L2 정규화를 확인한다.
- 내부 API 인증키만 X-API-Key로 보내며 Google 자격 증명·사용자 세션 쿠키를 전달하지 않는다. 외부 오류 본문·프롬프트·키를 오류 응답에 포함하지 않는다.
- 앱 시작 시 호출하지 않으며 서비스 코드에 자동 재시도는 없다. 접속 주소·키·제한 시간은 Spring 환경변수로 설정한다.

첫 추천 API에서 이 서비스를 사용할 때 적용하는 오류는 다음과 같다. 공통 code/message 구조로 변환한다.

| 상황 | 상태 | 코드 |
| --- | --- | --- |
| 비어 있거나 문자/인코딩 제한을 넘는 입력 | 400 | INVALID_EMBEDDING_INPUT |
| FastAPI의 입력·토큰 상한 거절 | 400 | EMBEDDING_INPUT_REJECTED |
| 연결 실패·제한 시간 초과·내부 인증/서버 오류 | 503 | EMBEDDING_UNAVAILABLE |
| FastAPI가 처리 중 | 503 | EMBEDDING_BUSY |
| GPU 준비 상태 불일치 | 503 | EMBEDDING_GPU_REQUIRED |
| 사전 벡터와 모델 설정 불일치 | 503 | EMBEDDING_MODEL_MISMATCH |
| 잘못된 JSON·응답 참조·벡터 | 502 | EMBEDDING_INVALID_RESPONSE |

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

로그아웃 문서는 Security 필터가 처리하는 경로를 OpenAPI에 등록한 것이다. 구글 로그인은 페이지 이동으로 진행하며, Swagger의 일반 API 실행으로 구글 인증 화면을 호출하지 않는다. 클라이언트 비밀값·세션 쿠키를 입력할 필요는 없다. 위 절차는 사용자 검증 완료 이후에도 회귀 확인 안내로 유지한다.

## 첫 추천 입력과 구현 상태

아래 도서 정보·첫 발췌 생성·발췌 재조회는 코드 작성 상태이며 실행 검증 전이다. 대비·전체 독서 경로는 후속 제안이다. 신규 테스트 코드는 사용자 요청으로 작성하지 않았다.

| 필드 | 입력 계약 |
| --- | --- |
| requestedPageCount | 필수 JSON 정수 1~10. 숫자 문자열·소수 거절 |
| storyType | 필수 WARM/HUMOR/THOUGHTFUL/ANY 중 하나. 대소문자 구분 |
| prompt | 선택 문자열 또는 null. UTF-16 2,000단위 이하. 공백만 있으면 입력 없음 |

프롬프트는 공백만 있는 입력을 제외하면 원문을 유지한다. NUL·고립 서로게이트 등 잘못된 인코딩은 400이다. 임베딩 모델의 실제 토큰 상한도 적용한다. 내부 bigint ID는 응답에서 문자열이며 제공 자료의 외부 ID와 혼용하지 않는다.

## 도서 조회

| 메서드·경로 | 결과 |
| --- | --- |
| GET /api/books/{bookId} | 200. 승인된 작품 정보·전체 쪽수·판본·언어·출처·이용 조건·번역 안내 |

bookId는 양의 정수인 내부 ID다. 존재하지 않거나 미승인·페이지 미준비 자료는 404 / BOOK_NOT_FOUND다. 전체 본문은 이 API로 반환하지 않는다. 도서 응답 필드는 아래 발췌 응답의 book과 같다.

## 첫 발췌 생성·재조회

| 메서드·경로 | 요청 | 결과 |
| --- | --- | --- |
| POST /api/previews | requestedPageCount·storyType·선택 prompt | 201. 첫 발췌와 Location: /api/previews/{previewId} |
| GET /api/previews/{previewId} | 같은 로그인 세션·응답 UUID | 200. GPU 재호출 없이 동일 발췌 범위 조회 |

POST에는 로그인 쿠키와 GET /api/csrf의 실제 token을 X-CSRF-TOKEN 헤더로 전달한다. GET은 로그인만 필요하다. 삭제된 회원은 401이다. 응답은 Cache-Control: no-store다.

요청 예시:

```json
{"requestedPageCount":5,"storyType":"WARM","prompt":"다시 시작할 용기를 얻고 싶어요"}
```

응답 구조:

| 필드 | 의미 |
| --- | --- |
| previewId | 세션 발췌 UUID. 문자열 |
| book | bookId·title·author·totalPageCount·edition·editionLabel·language·workType·sourceName·sourceUrl·licenseUrl·licenseLabel·credits·translationNote |
| requestedPageCount / actualPageCount | 원래 요청량 / 실제 제공량 |
| startPageNo / endPageNo | 1-based 시작/끝 페이지. 끝 포함 |
| storyType | 선택한 카드 |
| recommendationType | RANDOM / CARD / PROMPT / CARD_AND_PROMPT |
| matchedPageNo | 벡터 검색 기준 페이지. RANDOM이면 null |
| reason | 실제 처리한 추천 방식의 고정 안내. 생성형 작품 해석이 아님 |
| pages | [{pageNo, content}]. 원본 페이지 순서·본문 유지 |

작품 정보는 book 객체에 모으고 bookId·title·totalPageCount를 최상위에 중복하지 않는다. licenseUrl·licenseLabel·credits 등 원본에 없는 출처 필드는 null을 유지한다. translationNote는 AI 번역·교열 전·중역 등 원본 안내를 제공한다.

### 추천 경로와 범위

- 카드만: 해당 사전 query 벡터와 본문 벡터의 코사인 유사도 최댓값 검색.
- 카드+프롬프트: 기본 0.4×카드 유사도+0.6×프롬프트 유사도. APP_RECOMMENDATION_PROMPT_WEIGHT로 프롬프트 비중을 설정하고 카드 비중은 1-설정값이다. 유한한 0~1만 허용한다.
- ANY+프롬프트: 프롬프트 query만으로 검색.
- ANY 단독: 승인된 유효 작품별 균등 무작위 선택 후 첫 페이지부터 제공. 모델·카드·본문 벡터 조회와 FastAPI 호출 없음.
- 벡터 검색은 활성 모델·자료 버전에 맞고 전체 페이지 벡터가 준비된 승인 작품을 사용한다. 동점은 내부 도서 ID·페이지·검색 구간 ID 오름차순이다. 프롬프트가 있을 때만 FastAPI를 호출한다.
- 실제 제공량 A=min(요청 N,작품 P). 검색 기준 페이지 M에서 시작=max(1,min(M-floor((A-1)/2),P-A+1)), 끝=시작+A-1이다. 검색 대목을 포함하는 연속 범위를 반환한다. ANY 단독은 M=1이다.
- 10쪽 요청에 총 8쪽 작품이면 성공 8쪽이며, 전체 20쪽 작품은 10쪽이다. 발췌를 전체 독서 기록이나 완료로 합산하지 않는다.

### 세션·오류

발췌 ID·회원 ID·원래 쪽수/카드/프롬프트·작품/본문 해시·전체 쪽수·실제 범위·모델 지문·추천 경로를 로그인 세션에 보관한다. 본문·벡터를 보관하지 않는다. 최대 10개이며 초과 시 생성 순서가 가장 오래된 발췌를 제거한다. 재조회는 보관 순서를 바꾸지 않는다. 별도 발췌 DB 테이블은 없다.

| 상황 | 상태 / 코드 |
| --- | --- |
| 입력 형식·범위·프롬프트 인코딩 오류 | 400 / INVALID_REQUEST |
| 비로그인·삭제 회원 | 401 / AUTHENTICATION_REQUIRED |
| POST CSRF 실패 | 403 / CSRF_INVALID |
| 유효한 승인 작품 없음 | 404 / NO_RECOMMENDATION_CANDIDATES |
| 세션 발췌 없음·제거됨·소유자 불일치 | 404 / PREVIEW_NOT_FOUND |
| 작품 미존재·미승인·페이지 미준비 | 404 / BOOK_NOT_FOUND |
| 저장했던 작품 본문 해시·쪽수 변경 | 409 / PREVIEW_CONTENT_CHANGED |
| 활성 모델·선택 카드·전체 본문 벡터 준비 부족 | 503 / RECOMMENDATION_NOT_READY |
| 실제 반환 페이지의 순서·길이·해시 오류 | 503 / CONTENT_UNAVAILABLE |

모델 설정을 읽을 수 없는 벡터 검색은 후보 판단 전에 503일 수 있다. 임베딩 연결/응답 오류는 상단 내부 임베딩 계약을 따른다. 세션 만료·로그아웃·서버 재시작 뒤에는 새 추천이 필요하며 비로그인 상태라면 필터가 먼저 401을 반환한다. 작품 권한·해시를 재조회 시 확인하며 생성 당시 본문을 세션에서 재노출하지 않는다. 입력·본문·벡터·내부 인증값은 로그나 오류에 넣지 않는다.

### 대비 발췌: 구현 전

POST /api/previews/{previewId}/contrast는 후속 제안 경로다. 첫 발췌 참조를 사용하고 완료한 전체 독서 세션을 요구하지 않는다. 기준/대상 양쪽 실제 발췌에 근거 범위가 포함된 승인 관계만 제공한다. 대비 축은 결·시선·어조·태도·결말·시대·배경 6개다. CSV 후보는 승인 근거가 아니며 정확한 근거 위치·설명을 별도로 검수한다.

두 번째 작품에도 원래 requestedPageCount를 적용한다. 요청 10쪽에 첫 작품 8쪽·대비 작품 20쪽이면 각각 8쪽·10쪽이다. 유효 근거가 없으면 대비만 생략하고 첫 책 전체 독서는 허용한다. relationId·contrastAxis·commonTopic·reason·양쪽 근거 참조와 세부 실패 계약은 R4에서 구현한다.

## 전체 독서

| 제안 메서드·경로 | 요청·역할 |
| --- | --- |
| `POST /api/reading-sessions` | bookId로 전체 독서 시작. 신규 시작은 서버가 첫 페이지·처음 위치 설정 |
| `GET /api/reading-sessions/current` | 기존 미완료 전체 독서 이어 읽기 |
| `GET /api/reading-sessions/{sessionId}` | 본인 세션·전체 작품 기준 진행 조회 |
| `GET /api/reading-sessions/{sessionId}/pages/{pageNo}` | 세션 작품의 페이지 조회 |
| `PATCH /api/reading-sessions/{sessionId}/progress` | pageNo·position 기반 저장. 입력·필드명 최종 합의 필요 |
| `POST /api/reading-sessions/{sessionId}/complete` | 전체 마지막 페이지 끝 도달 검증 후 전체 책 완료 |

- 신규 시작에서 클라이언트의 발췌 시작 페이지나 requestedPageCount를 전체 독서 범위로 사용하지 않는다. 서버가 승인된 전체 본문 범위를 결정한다.
- 회원·책마다 미완료 세션은 하나다. 미완료 세션이 있으면 시작 요청은 기존 세션·현재 위치를 반환하고, 없거나 이전 세션이 완료됐다면 새 세션을 첫 페이지에 생성한다. 다른 책은 별도 미완료 세션을 허용한다. 동시 요청도 동일 정책을 보장하도록 처리하며 신규/재사용의 정확한 응답 상태는 최종 계약에서 고정한다.
- 응답은 sessionId·bookId·전체 쪽수·현재 위치·전체 책 기준 진행량·상태를 표현한다. 현재 위치·최대 도달 위치·진행률·저장 버전을 구분한다. 현재 위치는 재개에, 최대 도달 위치는 감소하지 않는 진행률에 사용한다. 최대 도달 위치는 실제 모든 앞 페이지 열람 증명이 아니다. 필드명·진행률 세부 환산은 최종 계약에서 고정한다.
- 발췌 읽기는 전체 독서 진행량에 자동 합산하지 않는다. 발췌로 짧은 작품 전체를 봐도 전체 독서 완료로 만들지 않는다.
- 페이지 내 위치는 UTF-16 코드 단위 오프셋을 사용하는 초안이다. 실제 범위 검증과 다른 언어/인덱스의 변환을 명시한다.
- 진행 요청의 reading/paused, 완료의 completed 방향을 유지한다. 전체 끝 미도달·상태 충돌·중복 완료의 구체적 응답은 R3에서 확정한다.
- 진행 저장·중단·완료에는 조회 시 받은 version을 포함한다. 서버는 버전 확인과 갱신을 원자적으로 처리하고 성공 시 version을 증가시킨다. 불일치는 409와 최신 현재 위치·최대 도달 위치·상태·version을 반환한다. 과거 요청은 현재/최대 위치·상태를 변경하지 않는다. 프런트는 최신 기록을 반영하되 실패 요청의 version만 교체해 자동 재전송하지 않는다. 네트워크 재전송에도 동일 규칙을 적용한다. 409 추가 데이터의 정확한 본문 필드와 오류 코드는 계약 초안으로 정리한다.

## 내부 임베딩 연동과 보류

- FastAPI는 본문·카드 사전 생성과 요청 프롬프트 임베딩을 담당하고 Spring은 DB·검색 점수·정책·페이지를 담당한다.
- 내부 `GET /health`, `POST /embeddings`는 [인계 문서](../plans/fastapi-ai-handoff.md)의 제안 계약이다. 사용자 세션을 FastAPI로 전달하지 않는다.
- KURE-v1을 사용하며 벡터는 PostgreSQL double precision[]에 저장하는 SQL·수동 등록 코드를 작성했다. 모델 revision·실제 GPU 접속은 추가 확인한다. 기본 카드/프롬프트 비중은 40%/60%이고 첫 추천 프롬프트는 UTF-16 2,000단위·모델 토큰 상한을 적용한다. 첫 추천·도서 API는 코드 작성 상태이며 실행 검증 전이다. 대비·전체 독서는 구현 전이다.
- 기본 공유·공유 카드는 무기한 보류하며 전용 API를 필수 범위에 추가하지 않는다. 성과 측정·이벤트 계약 확장도 보류한다.
- 현재 schema.sql의 선택 범위 중심 reading_sessions·기존 user_preferences·recommendation_logs를 이 문서만으로 변경하지 않는다. 페이지·검색 구간·원본 ID·벡터 저장 변경 SQL은 별도로 작성했다. 발췌는 세션 임시 보관이며 전체 독서·CSV 대비 유형 스키마 전환은 R3/R4에서 검토한다.
