# 도서·페이지·임베딩 수동 등록

상태: SQL·Java 등록 명령·파일 계약 검사 작성. Codex는 Spring 빌드·검사·DB 적용·등록 명령을 실행하지 않았다. 실제 도서 벡터도 아직 생성 전이다.

## 저장 구조

| 테이블 | 저장 내용 |
| --- | --- |
| books | 내부/외부 ID, 자료 버전, 전체 쪽수·해시·pageJoiner, 출처·번역·챕터 메타데이터 |
| book_pages | 1-based 원본 표시 페이지, 본문, 페이지 해시, UTF-16 길이 |
| book_search_segments | 안정적인 구간 ID, 도서·페이지, UTF-16 시작 포함/끝 제외 위치, 구간 해시 |
| embedding_models | 모델 ID·고정 revision·차원·정규화·설정 JSON·fingerprint |
| book_segment_embeddings | 검색 구간·모델별 정규화 document 벡터 |
| card_embeddings | 모델별 WARM/HUMOR/THOUGHTFUL 검색 문장·원본 ID·해시·query 벡터 |
| recommendation_configuration | 전체 본문/카드 등록 완료 후 사용할 모델과 원본 자료 정보 |

벡터는 PostgreSQL 기본 double precision[]을 사용한다. 배열 길이·1차원·NULL·NaN·Infinity를 DB에서 제한하고 L2 정규화와 원본 연결은 파일 검증에서 확인한다. 초기 864개 구간은 Spring 추천 서비스에서 유사도를 계산하며 벡터 DB·pgvector·검색 인덱스를 추가하지 않는다. 후속 첫 추천 API의 전수 유사도 검색 코드를 작성했으나 실제 DB/벡터 실행은 미검증이다. [PostgreSQL 배열 문서](https://www.postgresql.org/docs/current/arrays.html)

전체 본문은 book_pages에 나누어 모두 저장한다. 페이지 사이에 page_joiner를 넣어 연결하면 원본 text를 복원할 수 있다. 전체 text 열과 검색용 본문 사본은 추가하지 않는다. source_metadata는 text/pages를 제외한 원본 필드를 보존한다. licenseURL·licenseLabel·credits의 NULL, 번역 주의사항, 0-based chapters 범위도 유지한다.

기존 book_segments·reading_sessions·book_relations는 삭제·변환하지 않는다. R3는 별도 book_reading_sessions로 전체 독서를 작성했고 기존 DB용은 002-whole-book-reading.sql이다. 독서 API 사용 전에 001 다음으로 적용한다. 기존 기록 자동 변환은 없다. 6개 대비 축은 R4 별도 book_contrast_relations·003 변경 SQL을 작성했다. 후보/검수 등록은 contrast-import.md를 따른다. 기존 도서는 외부 ID와 등록용 필드를 NULL로 유지할 수 있지만 새 검색 경로에는 검증된 페이지·벡터가 있어야 한다.

## SQL 적용

- 빈 DB: schema.sql만 한 번 적용한다.
- 기존 R1 DB: 백업 후 migrations/001-content-embeddings.sql만 한 번 적용한다. 이미 같은 변경이 반영됐으면 재실행하지 않는다.
- 자동 초기화·Flyway·추가 확장은 도입하지 않는다. 앱의 ddl-auto=validate, sql.init.mode=never를 유지한다.
- 변경 SQL은 기존 테이블을 삭제하거나 회원·독서 데이터를 초기화하지 않는다. 트랜잭션 내 DDL 실패 시 롤백한다.

## 입력 파일

도서만 등록할 때 catalog-dir에 아래 파일이 필요하다. ShortsB-AI의 prepare_catalog.py가 생성한 묶음을 사용한다.

- catalog-metadata.json: 본문 제외 작품 정보와 원본 챕터
- segments.jsonl: 페이지 전체 본문·안정 ID·원본 페이지/UTF-16 위치·해시
- manifest.json: provided-pages-utf16-v1 매핑·작품/페이지/구간 수·원본 자료 버전/파일 해시

현재 매핑 버전은 페이지 하나에 검색 구간 하나다. 추후 페이지 내부 분할이 필요하면 새 매핑 버전과 검증을 함께 추가한다. 페이지 순서·본문 해시를 임의 보정하지 않는다. manifest의 sourceSHA256은 준비 도구가 기록한 원본 파일 해시를 보존하는 값이며, 이 명령은 원본 books.json 파일 바이트를 다시 읽지 않는다. 페이지 본문 재결합 SHA-256은 직접 검증한다.

벡터 등록에는 아래 세 경로를 모두 지정한다.

- documents-dir: 본문 vectors.jsonl·manifest.json
- cards-input: config/cards-v1.jsonl
- cards-dir: 카드 vectors.jsonl·manifest.json

본문/카드는 같은 모델 설정 fingerprint를 사용해야 한다. schemaVersion=1, 고정 40자리 revision, 양쪽 GPU 생성 정보, 라이브러리 4종 버전, 역할·ID·순서·위치·해시·개수·벡터 차원·정규화를 검사한다. fingerprint는 encoder.py와 같이 지문 대상 설정을 Python 기본 ensure_ascii=True·키 정렬·공백 없는 JSON으로 직렬화한 SHA-256이다. GPU 표기는 manifest와의 일관성을 검사하는 것이며 GPU 실행을 독립적으로 증명하는 것은 아니다.

## 사용자 실행 절차

아래는 사용자 실행용 명령이며 Codex가 실행한 결과가 아니다. 저장소 루트 기준이며 경로에 공백이 있으면 각 인수의 경로를 따옴표로 감싼다.

먼저 DB 접속 없이 파일만 검증한다. 기존 로컬 생성 경로 예시다.

```bash
./gradlew importCatalog --args='--catalog-dir ../ShortsB-AI/data/catalog-v1 --validate-only'
```

도서 등록 시에는 SQL 적용 후 실행 설정에 아래 기존 환경변수가 필요하다. .env.example 파일은 자동으로 읽지 않는다. IntelliJ에서 CatalogImportCommand.main을 직접 실행해도 된다. 이 명령은 SpringApplication을 시작하지 않으므로 local/prod YAML·Google 키·인증 설정을 읽지 않는다.

| 변수 | 용도 |
| --- | --- |
| SPRING_DATASOURCE_URL | 적용할 PostgreSQL JDBC 주소 |
| SPRING_DATASOURCE_USERNAME | 해당 DB 등록 권한이 있는 계정 |
| SPRING_DATASOURCE_PASSWORD | 해당 계정 비밀번호 |

```bash
./gradlew importCatalog --args='--catalog-dir ../ShortsB-AI/data/catalog-v1 --apply'
```

GPU에서 실제 벡터를 생성하고 산출물을 가져온 뒤, 도서+벡터 묶음을 검증·등록한다. output 경로는 실제 파일 위치로 지정한다.

```bash
./gradlew importCatalog --args='--catalog-dir ../ShortsB-AI/data/catalog-v1 --documents-dir ../ShortsB-AI/outputs/documents-v1 --cards-input ../ShortsB-AI/config/cards-v1.jsonl --cards-dir ../ShortsB-AI/outputs/cards-v1 --validate-only'
./gradlew importCatalog --args='--catalog-dir ../ShortsB-AI/data/catalog-v1 --documents-dir ../ShortsB-AI/outputs/documents-v1 --cards-input ../ShortsB-AI/config/cards-v1.jsonl --cards-dir ../ShortsB-AI/outputs/cards-v1 --apply'
```

--validate-only와 --apply 중 하나를 반드시 선택한다. 기본 서버 실행·시작 훅·공개 HTTP API로 등록하지 않는다. 도서만 등록하면 추천 모델 설정을 만들거나 변경하지 않는다. 벡터 전체와 카드 세 개를 등록한 경우만 같은 트랜잭션 끝에서 모델 설정을 반영한다. 일반 서버에서 등록할 때도 사전 벡터 파일을 가져와 사용하며 FastAPI에 본문 재임베딩을 요청하지 않는다.

## 재등록·공개·실패 처리

- 동일 외부 ID·메타데이터·페이지·구간·모델·벡터는 재사용하고 중복 행을 만들지 않는다. 기존 자료와 다른 값은 덮어쓰거나 삭제하지 않고 전체 등록을 거절한다. 원본 내용·자료 버전·카드 문장을 변경하려면 위치/벡터 영향 검토 후 별도 변경 절차를 작성한다.
- 같은 모델 설정에서 카드 문장만 변경하는 작업도 자동 덮어쓰지 않는다. 현재 카드 저장 키는 모델/카드이므로 새 검색 문구 버전 전환은 명시적인 후속 변경이 필요하다.
- 도서별 기존 페이지/검색 구간 수까지 대조하며 추가·누락된 기존 자료를 무단 정리하지 않는다. 동시 등록 명령은 PostgreSQL 트랜잭션 advisory lock으로 직렬화한다. 잠금 대기는 5초, 등록 트랜잭션은 120초다.
- content_rights는 신규 등록 시 unverified다. 구조 검증·벡터 등록으로 권한을 자동 승인하지 않는다. 재등록 시 기존 검토 상태와 rights_evidence를 유지한다. 추천 공개에는 별도 검수가 필요하다.
- 본문·입력 값·DB 비밀번호·원래 SQL 예외를 출력하지 않는다. 실패 시 예외 종류와 고정 안내만 출력하고 종료 코드 1을 반환한다. 성공 시 작품·페이지·본문 벡터·카드 개수만 출력한다. SQL/바인딩 TRACE 로깅은 켜지 않는다.

## 사용자 검증

```bash
./gradlew test --tests 'com.shortsb.book.service.CatalogImportReaderTests'
```

합성 자료로 원본 보존·UTF-16·해시·챕터·중복·인코딩·JSON 모호성·GPU/모델 호환·ID·벡터·카드 누락·Python JSON 지문 계약을 확인하는 검사 8개를 작성했다. 빌드·실행 전이며 통과 결과가 아니다.

실제 DB 확인은 사용자 테스트 DB에서 수행한다. 도서 등록 직후 현재 제공 자료 기준 작품 15개·페이지 864개·검색 구간 864개인지 확인한다. 도서만 등록한 경우 벡터/추천 설정은 아직 없다. 벡터 묶음 등록 후에는 해당 모델의 본문 벡터 864개·카드 3개와 추천 설정의 모델 지문을 확인한다. 기존 데이터가 있는 DB에서는 전체 테이블 수 대신 이번 외부 ID·모델로 범위를 제한한다.

동일 묶음 재등록 시 행 수 유지, 기존 데이터와 다른 입력 거절, 중간 DB 오류 시 신규 행과 모델 설정 롤백, 기존 회원·인증 유지도 확인한다. 모의 파일 검사만으로 DB 제약·JDBC·트랜잭션이 검증됐다고 기록하지 않는다.
