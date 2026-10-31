package com.shortsb.auth.service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

// 구글 인증 사용자와 내부 회원 연결 계약
public interface GoogleOidcUserService extends OAuth2UserService<OidcUserRequest, OidcUser> {
}
