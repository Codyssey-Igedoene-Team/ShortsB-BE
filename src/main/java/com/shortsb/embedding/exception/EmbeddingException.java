package com.shortsb.embedding.exception;

import org.springframework.http.HttpStatus;

// 원문·인증값·외부 오류 본문을 제외한 임베딩 오류
public class EmbeddingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public EmbeddingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
