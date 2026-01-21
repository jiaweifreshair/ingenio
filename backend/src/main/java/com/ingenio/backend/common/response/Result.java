package com.ingenio.backend.common.response;

import com.ingenio.backend.common.exception.ErrorCode;

import java.io.Serializable;

/**
 * 统一响应结果类
 * 封装所有API的返回结果
 *
 * @param <T> 数据类型
 */
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

    public Result() {
    }

    public Result(String code, String message, T data, Long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ResultBuilder<T> builder() {
        return new ResultBuilder<>();
    }

    public static class ResultBuilder<T> {
        private String code;
        private String message;
        private T data;
        private Long timestamp;

        public ResultBuilder<T> code(String code) {
            this.code = code;
            return this;
        }

        public ResultBuilder<T> message(String message) {
            this.message = message;
            return this;
        }

        public ResultBuilder<T> data(T data) {
            this.data = data;
            return this;
        }

        public ResultBuilder<T> timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Result<T> build() {
            return new Result<>(code, message, data, timestamp);
        }
    }

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

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
