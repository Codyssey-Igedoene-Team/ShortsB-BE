package com.shortsb.auth.config;

import com.shortsb.auth.service.GoogleOidcUserService;
import com.shortsb.common.exception.ApiErrorWriter;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
// 구글 로그인·세션·CSRF·접근 권한 설정
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    // 인증·CSRF 필터와 프로필별 문서 접근 구성
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties properties,
            GoogleOidcUserService googleUserService, ApiErrorWriter errors, Environment environment) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> {
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers(HttpMethod.GET, "/oauth2/authorization/google",
                                    "/login/oauth2/code/google", "/api/csrf").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("local & !prod"))) {
                        authorize.requestMatchers(HttpMethod.GET, SWAGGER_PATHS).permitAll();
                    } else {
                        authorize.requestMatchers(SWAGGER_PATHS).denyAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errors.write(response, 401, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrfFailure = exception instanceof CsrfException;
                            errors.write(response, 403, csrfFailure ? "CSRF_INVALID" : "ACCESS_DENIED",
                                    csrfFailure ? "CSRF 토큰을 다시 확인해주세요." : "접근 권한이 없습니다.");
                        }))
                .oauth2Login(oauth -> oauth
                        .loginPage("/oauth2/authorization/google")
                        .userInfoEndpoint(info -> info.oidcUserService(googleUserService))
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(properties.loginSuccessUrl()))
                        .failureHandler((request, response, exception) -> {
                            log.warn("구글 로그인 실패: {}", exception instanceof OAuth2AuthenticationException oauthException
                                    ? oauthException.getError().getErrorCode() : exception.getClass().getSimpleName());
                            response.sendRedirect(properties.loginFailureUrl());
                        }))
                .logout(logout -> logout
                        // 인가 필터보다 먼저 실행되는 LogoutFilter에서 로그인 상태 확인
                        .logoutRequestMatcher(request -> "POST".equals(request.getMethod())
                                && "/api/logout".equals(request.getServletPath()) && isAuthenticated())
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        return http.build();
    }

    // 익명 인증을 제외한 로그인 상태 확인
    private static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    // 허용 출처의 쿠키 포함 API 요청 설정
    private static CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
