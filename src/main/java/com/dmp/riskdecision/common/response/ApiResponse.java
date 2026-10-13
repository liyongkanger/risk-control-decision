package com.dmp.riskdecision.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 所有业务 HTTP 接口统一使用的响应结构。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS.name(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResponse<T> failure(ResultCode resultCode, String message, T data) {
        return new ApiResponse<>(resultCode.name(), message, data);
    }

    public static <T> ApiResponse<T> failure(ResultCode resultCode) {
        return failure(resultCode, resultCode.getMessage(), null);
    }
}
