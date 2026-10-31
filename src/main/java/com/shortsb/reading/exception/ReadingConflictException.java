package com.shortsb.reading.exception;

import com.shortsb.reading.dto.ReadingResponse;

// 본인 세션의 최신 상태를 포함하는 저장 충돌
public class ReadingConflictException extends RuntimeException {

    private final String code;
    private final ReadingResponse current;

    public ReadingConflictException(String code, String message, ReadingResponse current) {
        super(message);
        this.code = code;
        this.current = current;
    }

    public String code() {
        return code;
    }

    public ReadingResponse current() {
        return current;
    }
}
