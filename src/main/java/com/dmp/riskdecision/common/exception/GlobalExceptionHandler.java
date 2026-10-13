package com.dmp.riskdecision.common.exception;

import com.dmp.riskdecision.common.response.ApiResponse;
import com.dmp.riskdecision.common.response.ResultCode;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.policy.service.SceneCodeConflictException;
import com.dmp.riskdecision.policy.service.FlowCodeConflictException;
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
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GraphCompilationException.class)
    ResponseEntity<ApiResponse<List<GraphIssue>>> graphError(GraphCompilationException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiResponse.failure(ResultCode.GRAPH_INVALID, error.getMessage(), error.getIssues()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Void>> invalidArgument(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiResponse.failure(ResultCode.INVALID_REQUEST, error.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ResultCode.VERSION_CONFLICT, error.getMessage(), null));
    }

    @ExceptionHandler(SceneCodeConflictException.class)
    ResponseEntity<ApiResponse<Void>> sceneCodeConflict(SceneCodeConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ResultCode.SCENE_CODE_CONFLICT, error.getMessage(), null));
    }

    @ExceptionHandler(FlowCodeConflictException.class)
    ResponseEntity<ApiResponse<Void>> flowCodeConflict(FlowCodeConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(ResultCode.FLOW_CODE_CONFLICT, error.getMessage(), null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiResponse<Void>> missing(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ResultCode.NOT_FOUND, error.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> malformedRequest(HttpMessageNotReadableException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(ResultCode.INVALID_REQUEST, "Malformed JSON request", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error) {
        LOG.error("[GlobalExceptionHandler][unexpected] unhandled request failure", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ResultCode.INTERNAL_ERROR));
    }
}
