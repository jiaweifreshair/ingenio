package com.ingenio.backend.common.response;

import com.ingenio.backend.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果类
 * 封装所有API的返回结果
 *
 * @param <T> 数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /**
     * 响应码
     */
    private String code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return Result.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(message)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 失败响应（错误码）
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return Result.<T>builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 失败响应（错误码 + 数据）
     */
    public static <T> Result<T> error(ErrorCode errorCode, T data) {
        return Result.<T>builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 失败响应（自定义错误码和消息）
     */
    public static <T> Result<T> error(String code, String message) {
        return Result.<T>builder()
            .code(code)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 失败响应（自定义错误码、消息和数据）
     */
    public static <T> Result<T> error(String code, String message, T data) {
        return Result.<T>builder()
            .code(code)
            .message(message)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(this.code);
    }
}
