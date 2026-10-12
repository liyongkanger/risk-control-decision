package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.compile.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(GraphCompilationException.class)
    ResponseEntity<ApiError> graphError(GraphCompilationException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("GRAPH_INVALID", error.getMessage(), error.issues()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidArgument(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("INVALID_REQUEST", error.getMessage(), List.of()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("VERSION_CONFLICT", error.getMessage(), List.of()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> missing(NoSuchElementException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", error.getMessage(), List.of()));
    }

    public record ApiError(String code, String message, List<GraphIssue> issues) {}
}
