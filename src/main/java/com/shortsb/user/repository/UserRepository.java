package com.shortsb.user.repository;

import com.shortsb.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 회원 조회와 중복 생성 방지 저장소
public interface UserRepository extends JpaRepository<User, Long> {

    // 제공자와 고유 식별자로 회원 조회
    Optional<User> findByOauthProviderAndOauthSubject(String provider, String subject);

    // 소셜 계정 유일 키 충돌만 무시하고 다른 무결성 오류는 전파
    @Modifying
    @Query(value = """
            INSERT INTO public.users (oauth_provider, oauth_subject, email, nickname)
            VALUES (:provider, :subject, :email, :nickname)
            ON CONFLICT (oauth_provider, oauth_subject) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("provider") String provider,
                        @Param("subject") String subject,
                        @Param("email") String email,
                        @Param("nickname") String nickname);
}
