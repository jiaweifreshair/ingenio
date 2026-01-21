package com.ingenio.backend.common;

/**
 * 统一响应结果
 */
public class Result<T> {

    private Integer code;
    private Boolean success;
    private String message;
    private T data;
    private Long timestamp;

    public Result() {
    }

    public Result(Integer code, Boolean success, String message, T data, Long timestamp) {
        this.code = code;
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ResultBuilder<T> builder() {
        return new ResultBuilder<T>();
    }

    public static class ResultBuilder<T> {
        private Integer code;
        private Boolean success;
        private String message;
        private T data;
        private Long timestamp;

        public ResultBuilder<T> code(Integer code) {
            this.code = code;
            return this;
        }

        public ResultBuilder<T> success(Boolean success) {
            this.success = success;
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
            return new Result<T>(code, success, message, data, timestamp);
        }
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .success(true)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> success(T data, String message) {
        return Result.<T>builder()
                .code(200)
                .success(true)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static Result<Void> successMessage(String message) {
        return Result.<Void>builder()
                .code(200)
                .success(true)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> error(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .success(false)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    // Getters and Setters
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
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
