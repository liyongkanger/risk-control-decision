package com.dmp.riskdecision.shared.web;

import com.dmp.riskdecision.orchestrator.compile.GraphCompilationException;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将业务与系统异常转换为统一的 HTTP 错误响应。 */
@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(GraphCompilationException.class)
    ResponseEntity<ApiErrorResp> graphError(GraphCompilationException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiErrorResp("GRAPH_INVALID", error.getMessage(), error.getIssues()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResp> invalidArgument(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiErrorResp("INVALID_REQUEST", error.getMessage(), List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResp> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResp("VERSION_CONFLICT", error.getMessage(), List.of()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiErrorResp> missing(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResp("NOT_FOUND", error.getMessage(), List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResp> malformedRequest(HttpMessageNotReadableException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResp("INVALID_REQUEST", "Malformed JSON request", List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResp> unexpected(Exception error) {
        LOG.error("[ApiErrorHandler][unexpected] unhandled request failure", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResp("INTERNAL_ERROR", "Internal server error", List.of()));
    }
}
