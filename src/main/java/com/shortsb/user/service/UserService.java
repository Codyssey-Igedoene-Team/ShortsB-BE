package com.shortsb.user.service;

import com.shortsb.user.dto.MeResponse;
import com.shortsb.user.entity.User;

// 회원 조회·생성 서비스 계약
public interface UserService {

    // 구글 계정의 기존 회원 조회 또는 최초 생성
    User findOrCreateGoogleUser(String subject, String email, String nickname);

    // 내부 회원 ID로 본인 정보 조회
    MeResponse getMe(Long userId);
}
