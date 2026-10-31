package com.shortsb.auth.config;

import com.shortsb.auth.controller.AuthController;
import com.shortsb.auth.security.LoginUser;
import com.shortsb.auth.service.GoogleOidcUserService;
import com.shortsb.common.exception.ApiErrorWriter;
import com.shortsb.user.controller.UserController;
import com.shortsb.user.dto.MeResponse;
import com.shortsb.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 DB·구글 자격 증명 없이 인증 필터와 컨트롤러 계약 검증
@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties = {
        "spring.config.name=r1-security-test",
        "app.auth.login-success-url=http://localhost:3000/",
        "app.auth.login-failure-url=http://localhost:3000/login",
        "app.auth.allowed-origins=http://localhost:3000"
})
@Import({SecurityConfig.class, ApiErrorWriter.class, SecurityConfigTests.OAuthConfiguration.class})
@ActiveProfiles("local")
class SecurityConfigTests {

    private static final long USER_ID = 9_007_199_254_740_993L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private GoogleOidcUserService googleUserService;

    @MockitoBean
    private UserService userService;

    // 비로그인 본인 조회의 JSON 401 검증
    @Test
    void anonymousMeReturnsJson401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    // 구글 로그인 시작의 인증 화면 이동 검증
    @Test
    void loginStartsWithGoogleRedirect() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://accounts.google.com/")));
    }

    // 요청 ID 무시·세션 회원 조회·문자열 ID 검증
    @Test
    void meUsesSessionIdentityAndReturnsStringId() throws Exception {
        when(userService.getMe(USER_ID))
                .thenReturn(new MeResponse(Long.toString(USER_ID), "reader@example.com", "독자"));

        mvc.perform(get("/api/me").param("userId", "1").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.toString(USER_ID)))
                .andExpect(jsonPath("$.email").value("reader@example.com"))
                .andExpect(jsonPath("$.oauthSubject").doesNotExist());
        verify(userService).getMe(USER_ID);
    }

    // 삭제 회원 조회의 인증 거절 검증
    @Test
    void deletedMemberReturnsJson401() throws Exception {
        when(userService.getMe(USER_ID)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/me").session(authenticatedSession()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    // 실제 CSRF 조회 토큰의 로그아웃 허용과 세션 무효화 검증
    @Test
    void logoutAcceptsTheActualCsrfEndpointTokenAndInvalidatesSession() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfValue csrf = csrf(session);

        mvc.perform(post("/api/logout").servletPath("/api/logout").session(session)
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        csrf(new MockHttpSession());
    }

    // CSRF 누락 시 로그아웃 거절과 세션 유지 검증
    @Test
    void logoutWithoutCsrfIsRejectedAndKeepsSession() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(post("/api/logout").servletPath("/api/logout").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(session.isInvalid()).isFalse();
    }

    // CSRF 위조 시 로그아웃 거절과 세션 유지 검증
    @Test
    void logoutWithForgedCsrfIsRejectedAndKeepsSession() throws Exception {
        MockHttpSession session = authenticatedSession();
        CsrfValue csrf = csrf(session);
        mvc.perform(post("/api/logout").servletPath("/api/logout").session(session)
                        .header(csrf.headerName(), "forged-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(session.isInvalid()).isFalse();
    }

    // 유효한 CSRF를 가진 비로그인 로그아웃 거절 검증
    @Test
    void anonymousLogoutWithValidCsrfStillRequiresLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfValue csrf = csrf(session);
        mvc.perform(post("/api/logout").servletPath("/api/logout").session(session)
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    // 설정한 출처만 쿠키 포함 요청 허용 검증
    @Test
    void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, "https://other.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private CsrfValue csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        var body = jsonMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfValue(body.get("token").stringValue(), body.get("headerName").stringValue());
    }

    private MockHttpSession authenticatedSession() {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-id-token", now, now.plusSeconds(60), Map.of("sub", "google-sub"));
        var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token);
        var authentication = new OAuth2AuthenticationToken(new LoginUser(USER_ID, oidcUser),
                oidcUser.getAuthorities(), "google");
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private record CsrfValue(String token, String headerName) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OAuthConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("test-client").clientSecret("test-secret")
                    .redirectUri("http://localhost:8080/login/oauth2/code/google").build());
        }
    }
}
