package com.ingenio.backend.service;

import java.time.Instant;
import java.util.UUID;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.auth.AuthResponse;
import com.ingenio.backend.dto.auth.LoginRequest;
import com.ingenio.backend.dto.auth.RegisterRequest;
import com.ingenio.backend.dto.auth.VerificationType;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;

    @Value("${sa-token.timeout}")
    private Long tokenTimeout;

    @Value("${ingenio.tenant.default-tenant-id}")
    private String defaultTenantId;

    /**
     * 用户注册
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        // 1. 检查用户名是否已存在
        UserEntity existingByUsername = userMapper.selectOne(
                new QueryWrapper<UserEntity>()
                        .eq("username", request.getUsername())
        );
        if (existingByUsername != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        // 2. 检查邮箱是否已存在
        UserEntity existingByEmail = userMapper.selectOne(
                new QueryWrapper<UserEntity>()
                        .eq("email", request.getEmail())
        );
        if (existingByEmail != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        // 3. 创建用户
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID()); // 手动生成UUID，因为ASSIGN_UUID生成String类型与UUID字段不兼容
        user.setTenantId(UUID.fromString(defaultTenantId)); // 设置租户ID（转换为UUID）
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(BCrypt.hashpw(request.getPassword())); // BCrypt加密
        user.setRole(UserEntity.Role.USER.getValue());
        user.setStatus(UserEntity.Status.ACTIVE.getValue());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        int inserted = userMapper.insert(user);
        if (inserted == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户注册失败");
        }

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        // 4. 自动登录
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail(request.getUsername());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest);
    }

    /**
     * 用户登录
     *
     * 添加重试机制：当数据库连接暂时不可用时，最多重试3次
     * 重试间隔：1秒 → 2秒 → 4秒（指数退避）
     */
    @Retryable(
        retryFor = {
            CannotGetJdbcConnectionException.class,
            DataAccessResourceFailureException.class,
            MyBatisSystemException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public AuthResponse login(LoginRequest request) {
        // 1. 查询用户（支持用户名或邮箱登录）
        UserEntity user = userMapper.selectOne(
                new QueryWrapper<UserEntity>()
                        .and(wrapper -> wrapper
                                .eq("username", request.getUsernameOrEmail())
                                .or()
                                .eq("email", request.getUsernameOrEmail())
                        )
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 验证密码
        if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 3. 检查用户状态
        if (!UserEntity.Status.ACTIVE.getValue().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 4. 生成Token（SaToken自动生成JWT）
        StpUtil.login(user.getId().toString());
        String token = StpUtil.getTokenValue();

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 构造响应（避免运行时 Lombok Builder 类缺失导致登录失败）
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUserId(user.getId().toString());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setExpiresIn(tokenTimeout);
        return response;
    }

    /**
     * 退出登录
     */
    public void logout() {
        String userId = StpUtil.getLoginIdAsString();
        StpUtil.logout();
        log.info("用户退出登录: userId={}", userId);
    }

    /**
     * 获取当前用户信息
     *
     * 添加重试机制：当数据库连接暂时不可用时，最多重试3次
     */
    @Retryable(
        retryFor = {
            CannotGetJdbcConnectionException.class,
            DataAccessResourceFailureException.class,
            MyBatisSystemException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public UserEntity getCurrentUser() {
        String userId = StpUtil.getLoginIdAsString();
        return userMapper.findByIdWithCast(userId).orElse(null);
    }

    /**
     * 检查用户是否登录
     */
    public boolean isLogin() {
        return StpUtil.isLogin();
    }
}
