package com.ingenio.backend.common.exception;

import lombok.Getter;

/**
 * 错误码枚举
 * 定义系统中所有的错误码
 */
@Getter
public enum ErrorCode {

    // 通用错误码（1000-1999）
    SUCCESS("0000", "操作成功"),
    SYSTEM_ERROR("1000", "系统错误"),
    PARAM_ERROR("1001", "参数错误"),
    NOT_FOUND("1002", "资源不存在"),
    UNAUTHORIZED("1003", "未授权"),
    FORBIDDEN("1004", "无权限"),
    TOO_MANY_REQUESTS("1005", "请求过于频繁"),

    // 用户相关错误码（2000-2999）
    USER_NOT_FOUND("2000", "用户不存在"),
    USER_ALREADY_EXISTS("2001", "用户已存在"),
    USER_PASSWORD_ERROR("2002", "密码错误"),
    USER_DISABLED("2003", "用户已禁用"),
    USER_NOT_LOGIN("2004", "用户未登录"),

    // AppSpec相关错误码（3000-3999）
    APPSPEC_NOT_FOUND("3000", "AppSpec不存在"),
    APPSPEC_INVALID("3001", "AppSpec格式错误"),
    APPSPEC_VERSION_NOT_FOUND("3002", "AppSpec版本不存在"),
    APPSPEC_QUALITY_TOO_LOW("3003", "AppSpec质量评分过低"),

    // 代码生成相关错误码（4000-4999）
    CODEGEN_FAILED("4000", "代码生成失败"),
    CODEGEN_BUILD_FAILED("4001", "代码构建失败"),
    CODEGEN_PREVIEW_FAILED("4002", "预览生成失败"),

    // 项目相关错误码（5000-5999）
    PROJECT_NOT_FOUND("5000", "项目不存在"),
    PROJECT_ACCESS_DENIED("5001", "无权访问项目"),
    PROJECT_ALREADY_FORKED("5002", "已派生过该项目"),

    // AI Agent相关错误码（6000-6999）
    AGENT_PLAN_FAILED("6000", "需求规划失败"),
    AGENT_EXECUTE_FAILED("6001", "AppSpec生成失败"),
    AGENT_VALIDATE_FAILED("6002", "AppSpec验证失败"),
    AGENT_API_ERROR("6003", "AI API调用失败"),
    AGENT_TIMEOUT("6004", "AI处理超时"),

    // ExecuteGuard前置条件检查错误码（6100-6199）
    EXECUTE_GUARD_APPSPEC_NOT_FOUND("6100", "AppSpec不存在，无法执行Execute阶段"),
    EXECUTE_GUARD_PROTOTYPE_NOT_CONFIRMED("6101", "用户尚未确认设计方案，Execute阶段被阻塞"),
    EXECUTE_GUARD_NO_FRONTEND_PROTOTYPE("6102", "前端原型代码缺失，请先生成原型"),
    EXECUTE_GUARD_PROTOTYPE_URL_INVALID("6103", "前端原型预览URL无效或不可访问"),
    EXECUTE_GUARD_NO_SELECTED_STYLE("6104", "未选择设计风格，请先完成风格选择"),
    EXECUTE_GUARD_INTENT_NOT_CLASSIFIED("6105", "意图尚未识别，请先进行意图分类"),

    // 文件存储相关错误码（7000-7999）
    STORAGE_UPLOAD_FAILED("7000", "文件上传失败"),
    STORAGE_DOWNLOAD_FAILED("7001", "文件下载失败"),
    STORAGE_NOT_FOUND("7002", "文件不存在"),

    // 功能未实现错误码（8000-8999）
    NOT_IMPLEMENTED("8000", "功能未实现");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息
     */
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
