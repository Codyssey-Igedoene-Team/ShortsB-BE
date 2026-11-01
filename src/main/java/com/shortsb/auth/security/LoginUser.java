package com.shortsb.auth.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

// 내부 회원 ID와 구글 인증 정보를 담은 세션 사용자
public record LoginUser(Long userId, OidcUser oidcUser) implements OidcUser, Serializable {

    @Override
    public Map<String, Object> getClaims() {
        return oidcUser.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUser.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return oidcUser.getIdToken();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oidcUser.getAuthorities();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oidcUser.getAttributes();
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
