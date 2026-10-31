package com.shortsb.user.service.impl;

import com.shortsb.user.dto.MeResponse;
import com.shortsb.user.entity.User;
import com.shortsb.user.repository.UserRepository;
import com.shortsb.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
// 회원 조회·생성과 트랜잭션 처리 구현
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // 동시 가입 시 중복 생성 방지와 확인된 이메일 갱신
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public User findOrCreateGoogleUser(String subject, String email, String nickname) {
        User user = userRepository.findByOauthProviderAndOauthSubject("google", subject)
                .orElseGet(() -> {
                    userRepository.insertIfAbsent("google", subject, email, nickname);
                    // 동시 INSERT 완료 후 새 조회로 기존 생성 회원 확인
                    return userRepository.findByOauthProviderAndOauthSubject("google", subject)
                            .orElseThrow(() -> new IllegalStateException("회원 생성 결과를 확인할 수 없습니다."));
                });
        user.updateEmail(email);
        return user;
    }

    // 읽기 전용 회원 조회와 삭제 회원의 인증 거절
    @Override
    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return MeResponse.from(user);
    }
}
