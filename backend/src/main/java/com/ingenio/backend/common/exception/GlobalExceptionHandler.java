package com.ingenio.backend.common.exception;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.ingenio.backend.common.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理系统中的所有异常
 *
 * 异常分类：
 * 1. SaToken认证授权异常（401/403）
 * 2. 业务异常（400）
 * 3. 参数校验异常（400）
 * 4. 系统异常（500）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理SaToken未登录异常
     * 当用户未登录或Token失效时抛出
     *
     * 异常类型：
     * - NotLoginException.NOT_TOKEN: 未提供Token
     * - NotLoginException.INVALID_TOKEN: Token无效
     * - NotLoginException.TOKEN_TIMEOUT: Token已过期
     * - NotLoginException.BE_REPLACED: Token被顶下线
     * - NotLoginException.KICK_OUT: Token被踢下线
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Object> handleNotLoginException(NotLoginException e) {
        String message;
        switch (e.getType()) {
            case NotLoginException.NOT_TOKEN:
                message = "未提供认证Token，请先登录";
                break;
            case NotLoginException.INVALID_TOKEN:
                message = "Token无效，请重新登录";
                break;
            case NotLoginException.TOKEN_TIMEOUT:
                message = "Token已过期，请重新登录";
                break;
            case NotLoginException.BE_REPLACED:
                message = "您的账号在其他设备登录，已被强制下线";
                break;
            case NotLoginException.KICK_OUT:
                message = "您已被管理员强制下线";
                break;
            default:
                message = "未登录或登录已失效，请重新登录";
        }

        log.warn("用户未登录异常: type={}, message={}", e.getType(), message);
        return Result.error("401", message);
    }

    /**
     * 处理SaToken无权限异常
     * 当用户尝试访问需要特定权限的资源时抛出
     *
     * 示例：@SaCheckPermission("user:delete")
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Object> handleNotPermissionException(NotPermissionException e) {
        String permission = e.getPermission();
        String message = String.format("无权限访问：缺少权限 [%s]", permission);

        log.warn("用户无权限异常: permission={}", permission);
        return Result.error("403", message);
    }

    /**
     * 处理SaToken无角色异常
     * 当用户尝试访问需要特定角色的资源时抛出
     *
     * 示例：@SaCheckRole("admin")
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Object> handleNotRoleException(NotRoleException e) {
        String role = e.getRole();
        String message = String.format("无权限访问：缺少角色 [%s]", role);

        log.warn("用户无角色异常: role={}", role);
        return Result.error("403", message);
    }

    /**
     * 处理SaToken账号被封禁异常
     * 当用户账号被封禁时抛出
     *
     * 示例：StpUtil.disable(userId, 3600)
     */
    @ExceptionHandler(DisableServiceException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Object> handleDisableServiceException(DisableServiceException e) {
        long disableTime = e.getDisableTime();
        String message;

        if (disableTime > 0) {
            long minutes = disableTime / 60;
            long hours = minutes / 60;

            if (hours > 0) {
                message = String.format("您的账号已被封禁，解封时间：%d小时后", hours);
            } else if (minutes > 0) {
                message = String.format("您的账号已被封禁，解封时间：%d分钟后", minutes);
            } else {
                message = String.format("您的账号已被封禁，解封时间：%d秒后", disableTime);
            }
        } else {
            message = "您的账号已被永久封禁，请联系管理员";
        }

        log.warn("账号被封禁异常: disableTime={}, message={}", disableTime, message);
        return Result.error("403", message);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Object> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage(), e.getData());
    }

    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验异常: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return Result.error(ErrorCode.PARAM_ERROR, errors);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Map<String, String>> handleBindException(BindException e) {
        log.warn("参数绑定异常: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return Result.error(ErrorCode.PARAM_ERROR, errors);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 处理数据库连接获取失败异常
     * 当连接池耗尽或数据库暂时不可用时抛出
     *
     * 返回JSON格式的友好错误信息，避免前端收到纯文本"Internal Server Error"
     */
    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Object> handleCannotGetJdbcConnectionException(CannotGetJdbcConnectionException e) {
        log.error("数据库连接获取失败: {}", e.getMessage());
        return Result.error("503", "服务繁忙，请稍后重试");
    }

    /**
     * 处理MyBatis系统异常
     * 通常由数据库连接问题或SQL执行错误导致
     *
     * 返回JSON格式的友好错误信息，避免前端收到纯文本"Internal Server Error"
     */
    @ExceptionHandler(MyBatisSystemException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Object> handleMyBatisSystemException(MyBatisSystemException e) {
        Throwable cause = e.getCause();

        // 检查是否是连接问题
        if (cause instanceof CannotGetJdbcConnectionException ||
                (cause != null && cause.getMessage() != null &&
                        cause.getMessage().contains("Failed to obtain JDBC Connection"))) {
            log.error("MyBatis数据库连接失败: {}", e.getMessage());
            return Result.error("503", "服务繁忙，请稍后重试");
        }

        log.error("MyBatis系统异常: {}", e.getMessage(), e);
        return Result.error("500", "数据访问异常，请稍后重试");
    }

    /**
     * 处理数据访问资源失败异常
     * 包括连接池耗尽、数据库宕机等场景
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Object> handleDataAccessResourceFailureException(DataAccessResourceFailureException e) {
        log.error("数据访问资源失败: {}", e.getMessage());
        return Result.error("503", "服务繁忙，请稍后重试");
    }

    /**
     * 处理SQL异常
     * 包括连接超时、查询超时等数据库相关错误
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Object> handleSQLException(SQLException e) {
        log.error("SQL异常: SQLState={}, ErrorCode={}, Message={}",
                e.getSQLState(), e.getErrorCode(), e.getMessage());
        return Result.error("503", "数据库服务暂时不可用，请稍后重试");
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Object> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
