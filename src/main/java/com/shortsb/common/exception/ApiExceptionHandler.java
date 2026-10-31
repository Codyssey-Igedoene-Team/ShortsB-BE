package com.shortsb.common.exception;

import com.shortsb.common.dto.ApiError;
import com.shortsb.embedding.exception.EmbeddingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
// MVC 예외의 공통 오류 응답 변환
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // 업무 오류의 고정 코드·메시지 반환
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleBusiness(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    // 임베딩 연결 오류의 안전한 코드·상태 반환
    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<ApiError> handleEmbedding(EmbeddingException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    // MVC 오류 상태별 공통 코드와 메시지 매핑
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ApiError error = switch (status.value()) {
            case 400 -> new ApiError("INVALID_REQUEST", "요청값을 확인해주세요.");
            case 401 -> new ApiError("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
            case 403 -> new ApiError("ACCESS_DENIED", "접근 권한이 없습니다.");
            case 404 -> new ApiError("NOT_FOUND", "대상을 찾을 수 없습니다.");
            case 405 -> new ApiError("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
            case 409 -> new ApiError("STATE_CONFLICT", "현재 상태에서 처리할 수 없습니다.");
            case 415 -> new ApiError("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 콘텐츠 형식입니다.");
            default -> new ApiError("REQUEST_FAILED", "요청을 처리할 수 없습니다.");
        };
        return super.handleExceptionInternal(exception, error, headers, status, request);
    }

    // 접근 권한 오류의 403 응답
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCESS_DENIED", "접근 권한이 없습니다."));
    }

    // 예상하지 못한 예외의 500 응답과 내부 정보 노출 방지
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("요청 처리 중 예외 발생: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다."));
    }
}
