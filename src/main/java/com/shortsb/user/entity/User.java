package com.shortsb.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 소셜 로그인 회원 엔티티
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "oauth_provider", nullable = false, length = 20, updatable = false)
    private String oauthProvider;

    @Column(name = "oauth_subject", nullable = false, length = 255, updatable = false)
    private String oauthSubject;

    @Column(length = 50)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void updateEmail(String email) {
        this.email = email;
    }

    // 최초 저장 시 생성·수정 시각 초기화
    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    // 회원 정보 변경 시 수정 시각 갱신
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
