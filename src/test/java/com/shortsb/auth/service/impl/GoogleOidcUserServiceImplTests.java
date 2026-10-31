package com.shortsb.auth.service.impl;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.auth.service.GoogleOidcUserService;
import com.shortsb.user.entity.User;
import com.shortsb.user.service.UserService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 구글 사용자 정보 검증과 회원 연동 단위 테스트
@ExtendWith(MockitoExtension.class)
class GoogleOidcUserServiceImplTests {

    @Mock
    private UserService userService;

    private GoogleOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOidcUserServiceImpl(userService);
    }

    // 확인된 계정 연동과 유니코드 닉네임 길이 제한 검증
    @Test
    void verifiedIdentityCreatesPrincipalAndTruncatesNicknameByCodePoint() {
        User member = mock(User.class);
        when(member.getId()).thenReturn(42L);
        when(userService.findOrCreateGoogleUser(any(), any(), any())).thenReturn(member);

        LoginUser principal = (LoginUser) service.loadUser(request(true, "🙂".repeat(51)));

        assertThat(principal.userId()).isEqualTo(42L);
        verify(userService).findOrCreateGoogleUser("google-sub", "reader@example.com", "🙂".repeat(50));
    }

    // 미확인 이메일의 회원 생성 거절 검증
    @Test
    void unverifiedEmailCannotCreateMember() {
        assertThatThrownBy(() -> service.loadUser(request(false, "독자")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_user_info"));
        verifyNoInteractions(userService);
    }

    // 회원 저장 오류의 인증 실패 변환 검증
    @Test
    void storageFailureIsAuthenticationFailure() {
        when(userService.findOrCreateGoogleUser(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("test-storage-failure"));

        assertThatThrownBy(() -> service.loadUser(request(true, "독자")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("member_storage_failed"));
    }

    private OidcUserRequest request(boolean emailVerified, String name) {
        // user-info URI 생략으로 외부 호출 없는 ID 토큰 후처리 검증
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .scope("openid", "profile", "email")
                .build();
        Instant now = Instant.now();
        var idToken = new OidcIdToken("test-id-token", now, now.plusSeconds(60), Map.of(
                "sub", "google-sub", "email", "reader@example.com", "email_verified", emailVerified, "name", name));
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "test-access-token", now, now.plusSeconds(60));
        return new OidcUserRequest(registration, accessToken, idToken);
    }
}
