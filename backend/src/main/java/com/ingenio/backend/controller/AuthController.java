package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.auth.*;
import com.ingenio.backend.dto.user.ConfirmPasswordResetDTO;
import com.ingenio.backend.dto.user.ResetPasswordRequestDTO;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * 认证控制器
 * 提供用户注册、登录、密码重置等认证相关接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册、登录、退出、密码重置等认证相关接口")
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final WechatService wechatService;
    private final OAuthService oAuthService;
    private final VerificationCodeService verificationCodeService;
    private final UserMapper userMapper;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户账号")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return Result.success(response);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名/邮箱和密码登录")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return Result.success(response);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    @SaCheckLogin
    @Operation(summary = "退出登录", description = "清除用户登录状态")
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    @SaCheckLogin
    @Operation(summary = "获取当前用户", description = "获取当前登录用户的详细信息")
    public Result<UserEntity> getCurrentUser() {
        UserEntity user = userService.getCurrentUser();
        // 不返回密码
        user.setPasswordHash(null);
        return Result.success(user);
    }

    /**
     * 请求密码重置
     *
     * 功能：
     * 1. 验证邮箱是否存在
     * 2. 生成重置令牌
     * 3. 发送重置邮件
     */
    @PostMapping("/reset-password/request")
    @Operation(summary = "请求密码重置", description = "发送密码重置邮件")
    public Result<Void> requestPasswordReset(@Valid @RequestBody ResetPasswordRequestDTO request) {
        passwordResetService.createResetToken(request.getEmail());
        return Result.successMessage("密码重置邮件已发送，请检查您的邮箱");
    }

    /**
     * 验证重置令牌
     *
     * 功能：
     * 验证令牌是否有效（未过期且未使用）
     */
    @GetMapping("/reset-password/verify-token")
    @Operation(summary = "验证重置令牌", description = "检查重置令牌是否有效")
    public Result<Boolean> verifyResetToken(@RequestParam String token) {
        boolean isValid = passwordResetService.verifyResetToken(token);
        return Result.success(isValid);
    }

    /**
     * 确认密码重置
     *
     * 功能：
     * 1. 验证令牌有效性
     * 2. 验证密码强度
     * 3. 更新用户密码
     * 4. 标记令牌为已使用
     */
    @PostMapping("/reset-password/confirm")
    @Operation(summary = "确认密码重置", description = "使用重置令牌设置新密码")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetDTO request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());
        return Result.successMessage("密码重置成功");
    }

    /**
     * 生成微信登录二维码
     *
     * 功能：
     * 1. 生成唯一场景值（UUID）
     * 2. 返回微信登录二维码URL
     * 3. 在Redis中初始化状态为pending
     *
     * @return 二维码响应（包含qrcodeUrl, sceneStr, expiresIn）
     */
    @GetMapping("/wechat/qrcode")
    @Operation(summary = "生成微信登录二维码", description = "生成微信扫码登录的二维码URL和场景值")
    public Result<WxQrcodeResponse> generateWechatQrcode() {
        log.info("生成微信登录二维码");
        WxQrcodeResponse response = wechatService.generateWechatQrcode();
        return Result.success(response);
    }

    /**
     * 检查微信扫码状态（轮询接口）
     *
     * 功能：
     * 1. 根据场景值查询Redis中的扫码状态
     * 2. 返回当前状态：pending（未扫码）/ scanned（已扫码，等待确认）/ confirmed（已确认）/ expired（已过期）
     * 3. 如果已确认，返回Token和用户信息
     *
     * @param sceneStr 场景值（从生成二维码接口获取）
     * @return 扫码状态响应
     */
    @GetMapping("/wechat/check-scan")
    @Operation(summary = "检查微信扫码状态", description = "轮询接口，检查二维码扫描状态")
    public Result<WxScanStatusResponse> checkWechatScanStatus(@RequestParam String sceneStr) {
        log.debug("检查微信扫码状态: sceneStr={}", sceneStr);
        WxScanStatusResponse response = wechatService.checkWechatScanStatus(sceneStr);
        return Result.success(response);
    }

    /**
     * 微信回调处理（内部接口）
     *
     * 功能：
     * 1. 接收微信授权回调（code和state）
     * 2. 使用code换取access_token
     * 3. 获取微信用户信息
     * 4. 查找或创建系统用户
     * 5. 更新Redis状态为confirmed
     *
     * @param request 回调请求（包含code和state）
     * @return 成功响应
     */
    @PostMapping("/wechat/callback")
    @Operation(summary = "微信回调处理", description = "处理微信OAuth回调（内部接口）")
    public Result<Void> handleWechatCallback(@Valid @RequestBody WxCallbackRequest request) {
        log.info("处理微信回调: state={}", request.getState());
        wechatService.handleWechatCallback(request);
        return Result.successMessage("登录成功");
    }

    /**
     * Google OAuth回调处理
     *
     * 功能：
     * 1. 接收Google授权回调（code参数）
     * 2. 使用code换取access_token
     * 3. 获取Google用户信息
     * 4. 查找或创建系统用户
     * 5. 生成SaToken并返回
     *
     * @param request OAuth回调请求（包含code和可选的state）
     * @return 认证响应（包含Token和用户信息）
     */
    @PostMapping("/oauth/google/callback")
    @Operation(summary = "Google OAuth回调", description = "处理Google OAuth授权回调")
    public Result<AuthResponse> handleGoogleCallback(@Valid @RequestBody OAuthCallbackRequest request) {
        log.info("处理Google OAuth回调: code={}", request.getCode());
        AuthResponse response = oAuthService.loginWithGoogle(request.getCode());
        return Result.success(response);
    }

    /**
     * GitHub OAuth回调处理
     *
     * 功能：
     * 1. 接收GitHub授权回调（code参数）
     * 2. 使用code换取access_token
     * 3. 获取GitHub用户信息
     * 4. 查找或创建系统用户
     * 5. 生成SaToken并返回
     *
     * @param request OAuth回调请求（包含code和可选的state）
     * @return 认证响应（包含Token和用户信息）
     */
    @PostMapping("/oauth/github/callback")
    @Operation(summary = "GitHub OAuth回调", description = "处理GitHub OAuth授权回调")
    public Result<AuthResponse> handleGitHubCallback(@Valid @RequestBody OAuthCallbackRequest request) {
        log.info("处理GitHub OAuth回调: code={}", request.getCode());
        AuthResponse response = oAuthService.loginWithGitHub(request.getCode());
        return Result.success(response);
    }

    /**
     * 基于验证码的密码重置（Phase 6.2）
     *
     * 功能：
     * - 验证邮箱验证码（RESET_PASSWORD类型）
     * - 更新用户密码（BCrypt加密）
     * - 删除验证码（防止重复使用）
     * - 清除用户所有会话（强制重新登录）
     *
     * 安全机制：
     * - 验证码5分钟过期
     * - 验证码验证后立即删除
     * - 密码强度验证（8字符+大小写字母+数字）
     * - BCrypt加密存储
     * - 重置成功后强制登出所有设备
     *
     * POST /api/v1/auth/reset-password-by-code
     * Body: { email, code, newPassword }
     *
     * @param request 重置密码请求
     * @return 成功响应
     */
    @PostMapping("/reset-password-by-code")
    @Operation(summary = "基于验证码重置密码", description = "通过邮箱验证码重置用户密码（Phase 6.2新增）")
    public Result<Void> resetPasswordByCode(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("基于验证码重置密码请求: email={}", request.getEmail());

        // 1. 验证邮箱验证码（RESET_PASSWORD类型）
        try {
            verificationCodeService.verifyCode(
                    request.getEmail(),
                    request.getCode(),
                    VerificationType.RESET_PASSWORD
            );
        } catch (BusinessException e) {
            log.warn("验证码验证失败: email={}, error={}", request.getEmail(), e.getMessage());
            throw e; // 重新抛出验证码错误
        }

        // 2. 根据邮箱查找用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmail, request.getEmail())
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户不存在");
        }

        // 3. 加密新密码（使用SaToken BCrypt）
        String hashedPassword = BCrypt.hashpw(request.getNewPassword());

        // 4. 更新密码
        user.setPasswordHash(hashedPassword);
        user.setUpdatedAt(Instant.now());
        int updateResult = userMapper.updateById(user);

        if (updateResult == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "密码更新失败");
        }

        // 5. 清除该用户的所有会话（强制重新登录）
        StpUtil.logout(user.getId());

        log.info("密码重置成功: userId={}, email={}", user.getId(), user.getEmail());

        return Result.successMessage("密码重置成功，请使用新密码登录");
    }

    /**
     * Token刷新接口
     *
     * 功能：
     * - 验证当前Token有效性
     * - 生成新的Token并返回
     * - 自动延长用户登录状态
     *
     * 使用场景：
     * - 前端检测到Token即将过期时主动刷新
     * - 用户长时间未操作后再次使用应用
     *
     * @return 新的Token信息
     */
    @PostMapping("/refresh")
    @SaCheckLogin
    @Operation(summary = "刷新Token", description = "刷新当前用户的认证Token，延长登录状态")
    public Result<TokenRefreshResponse> refreshToken() {
        // 获取当前用户ID
        String userId = StpUtil.getLoginIdAsString();

        log.info("刷新Token: userId={}", userId);

        // SaToken的renewTimeout()方法会自动刷新Token的activity-timeout
        // 相当于"续命"操作，重置活动超时计时器
        StpUtil.renewTimeout(86400); // 重置为1天activity-timeout

        // 获取当前Token值
        String tokenValue = StpUtil.getTokenValue();

        // 获取Token剩余有效时间（秒）
        long tokenTimeout = StpUtil.getTokenTimeout();

        // 构建响应
        TokenRefreshResponse response = TokenRefreshResponse.builder()
            .token(tokenValue)
            .tokenType("Bearer")
            .expiresIn(tokenTimeout)
            .refreshedAt(Instant.now().getEpochSecond())
            .build();

        log.info("Token刷新成功: userId={}, newExpiresIn={}秒", userId, tokenTimeout);

        return Result.success(response);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查认证服务是否正常")
    public Result<String> health() {
        return Result.success("Auth service is running");
    }
}
