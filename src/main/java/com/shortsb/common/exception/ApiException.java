package com.shortsb.common.exception;

import org.springframework.http.HttpStatus;

// 입력값·본문을 포함하지 않는 업무 오류
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
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
