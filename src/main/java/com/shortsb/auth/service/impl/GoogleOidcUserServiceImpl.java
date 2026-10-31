package com.shortsb.auth.service.impl;

import com.shortsb.auth.security.LoginUser;
import com.shortsb.auth.service.GoogleOidcUserService;
import com.shortsb.user.entity.User;
import com.shortsb.user.service.UserService;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
// 구글 사용자 정보 검증과 회원 연동 구현
public class GoogleOidcUserServiceImpl extends OidcUserService implements GoogleOidcUserService {

    private final UserService userService;

    public GoogleOidcUserServiceImpl(UserService userService) {
        this.userService = userService;
    }

    // 검증된 구글 계정과 내부 회원 연결
    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) {
            throw invalidUserInfo();
        }
        OidcUser oidcUser = super.loadUser(request);
        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        if (subject == null || subject.isBlank() || subject.length() > 255
                || email == null || email.isBlank() || email.length() > 255
                || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw invalidUserInfo();
        }

        // 유니코드 문자 경계를 유지한 닉네임 50자 제한
        String nickname = oidcUser.getFullName();
        if (nickname != null) {
            nickname = nickname.strip();
            nickname = nickname.substring(0,
                    nickname.offsetByCodePoints(0, Math.min(nickname.codePointCount(0, nickname.length()), 50)));
            if (nickname.isBlank()) {
                nickname = null;
            }
        }

        try {
            User user = userService.findOrCreateGoogleUser(subject, email, nickname);
            return new LoginUser(user.getId(), oidcUser);
        } catch (DataAccessException | IllegalStateException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("member_storage_failed"), exception);
        }
    }

    private static OAuth2AuthenticationException invalidUserInfo() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"),
                "확인된 구글 계정 정보가 필요합니다.");
    }
}
