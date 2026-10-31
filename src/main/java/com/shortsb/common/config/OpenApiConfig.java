package com.shortsb.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local & !prod")
// 로컬 Swagger 안내와 필터 기반 로그아웃 문서 설정
public class OpenApiConfig {

    // 인증 테스트 순서와 로그아웃 요청·응답 문서 등록
    @Bean
    OpenAPI shortsbOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ShortSB API").version("R4")
                        .description("""
                                ## 로컬 인증 테스트 순서

                                1. [구글 로그인 시작](/oauth2/authorization/google)을 **새 탭에서 열어** 로그인하세요.
                                   완료 후 이 Swagger 탭으로 돌아옵니다. 세션 쿠키는 같은 브라우저에서 자동 전달됩니다.
                                   성공 URL의 프런트가 실행 중이 아니어도 아래 내 정보 조회로 로그인 결과를 확인할 수 있습니다.
                                2. **회원 → 로그인한 내 정보 조회 → Try it out → Execute**로 200과 회원 정보를 확인합니다.
                                3. **인증 → CSRF 토큰 조회 → Try it out → Execute** 후 실제 응답의 `token`을 복사합니다.
                                4. **인증 → 로그아웃 → Try it out**에서 `X-CSRF-TOKEN`에 복사한 토큰을 넣고 Execute합니다.
                                   정상 결과는 **204(응답 본문 없음)** 입니다.
                                5. 내 정보 조회를 다시 Execute하면 **401 / AUTHENTICATION_REQUIRED**가 반환돼야 합니다.

                                로그인·로그아웃 후에는 CSRF 토큰을 다시 조회하세요. Example Value의 값이 아닌 실제 응답 토큰을 사용합니다.
                                비로그인 상태의 내 정보 조회 401, 로그인 상태에서 잘못된 CSRF 토큰으로 로그아웃할 때 403도 확인할 수 있습니다.
                                구글 로그인은 위 링크로 페이지를 이동해 진행합니다. 클라이언트 비밀값이나 세션 쿠키를 문서에 입력하지 않습니다.

                                ## 첫 추천·발췌 사용 순서

                                로그인·CSRF 토큰 조회 후 **추천 발췌 → 첫 추천 발췌 생성**에서 X-CSRF-TOKEN과 요청 본문을 입력하세요.
                                요청 예시 네 가지로 카드만·프롬프트만·둘 다·ANY 단독 경로를 확인할 수 있습니다.
                                생성 응답의 previewId는 발췌 재조회에, book.bookId는 도서 정보 조회에 사용합니다.
                                실제 데이터 등록과 작품 권한 승인이 먼저 필요합니다. 벡터 검색은 본문/카드 벡터 등록이 필요하고,
                                프롬프트 입력은 FastAPI GPU 연결도 필요합니다.

                                ## 대비 발췌 사용 순서

                                첫 추천의 previewId와 현재 CSRF 토큰을 **대비 발췌 → 대비 작품 발췌 추천**에 입력하세요.
                                CSV 후보뿐 아니라 양쪽 작품 승인과 실제 근거 검수가 필요합니다.
                                200·available=false는 적합한 승인 관계가 없는 정상 결과입니다. 모델/본문 준비 오류 503과 구분합니다.
                                성공하면 contrast.book.bookId, 첫 작품이면 기존 book.bookId를 전체 독서 시작에 사용하세요.

                                ## 전체 독서 사용 순서

                                **전체 독서 → 시작**에서 bookId를 문자열로 입력하고 sessionId·version을 확인하세요.
                                페이지 조회 후 위치 저장에 pageNo·position·version·status를 전달합니다. position은 UTF-16 기준입니다.
                                중단 시 status를 paused로 저장하고, 최근 미완료 조회에서 이어 읽을 현재 위치를 확인합니다.
                                마지막 페이지 끝을 저장한 뒤 완료 요청에 최신 version을 전달하세요.
                                409 응답의 current를 반영하며 과거 요청의 version만 바꿔 자동 재전송하지 않습니다.
                                전체 독서 SQL 적용과 승인 도서 등록이 먼저 필요합니다. GPU 연결은 독서 API에 필요하지 않습니다.
                                """))
                // Security의 LogoutFilter가 실행하는 로그아웃 경로의 문서 등록
                .path("/api/logout", new PathItem().post(new Operation()
                        .operationId("logout")
                        .tags(List.of("인증"))
                        .summary("로그아웃")
                        .description("""
                                구글 로그인과 CSRF 토큰 조회를 먼저 수행하세요.
                                X-CSRF-TOKEN에 현재 응답의 token을 입력하면 세션을 무효화하고 JSESSIONID 쿠키를 제거합니다.
                                요청 본문은 없습니다. 성공 후 내 정보 조회는 401이어야 하며,
                                재로그인 후에는 새 CSRF 토큰이 필요합니다.
                                비로그인 요청은 유효한 CSRF 토큰이 있어도 401입니다. CSRF 검증이 먼저 실패하면 403입니다.
                                """)
                        .addParametersItem(new HeaderParameter().name("X-CSRF-TOKEN")
                                .required(true)
                                .description("GET /api/csrf의 실제 응답에서 복사한 token 값")
                                .schema(new StringSchema()))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("로그아웃 성공. 응답 본문 없음"))
                                .addApiResponse("401", errorResponse("로그인 필요 또는 세션 만료",
                                        "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."))
                                .addApiResponse("403", errorResponse("CSRF 토큰 누락·위조·이전 세션의 토큰",
                                        "CSRF_INVALID", "CSRF 토큰을 다시 확인해주세요.")))));
    }

    private static ApiResponse errorResponse(String description, String code, String message) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiError"))
                        .example(Map.of("code", code, "message", message))));
    }
}
