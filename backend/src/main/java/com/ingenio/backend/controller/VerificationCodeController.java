package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.auth.SendVerificationCodeRequest;
import com.ingenio.backend.dto.auth.VerifyCodeRequest;
import com.ingenio.backend.service.VerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 邮箱验证码Controller
 *
 * 功能：
 * 1. 发送邮箱验证码（POST /api/v1/auth/verification-code/send）
 * 2. 校验邮箱验证码（POST /api/v1/auth/verification-code/verify）
 *
 * 使用场景：
 * - 用户注册时验证邮箱
 * - 找回密码时验证身份
 * - 修改邮箱时验证新邮箱
 *
 * 安全机制：
 * - 60秒内限制发送1次
 * - 验证码5分钟过期
 * - 验证成功后立即删除
 *
 * @author Ingenio Team
 * @since Phase 5.1
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth/verification-code")
@RequiredArgsConstructor
@Tag(name = "邮箱验证码", description = "邮箱验证码发送和校验API")
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    /**
     * 发送邮箱验证码
     *
     * POST /api/v1/auth/verification-code/send
     * {
     *   "email": "user@example.com",
     *   "type": "REGISTER"
     * }
     *
     * @param request 发送验证码请求
     * @return 成功响应
     */
    @PostMapping("/send")
    @Operation(summary = "发送邮箱验证码", description = "发送6位数字验证码到指定邮箱，有效期5分钟")
    public Result<Void> sendCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        log.info("发送验证码请求: email={}, type={}", request.getEmail(), request.getType());

        verificationCodeService.sendCode(request.getEmail(), request.getType());

        return Result.successMessage("验证码已发送，请查收邮件");
    }

    /**
     * 验证邮箱验证码
     *
     * POST /api/v1/auth/verification-code/verify
     * {
     *   "email": "user@example.com",
     *   "code": "123456",
     *   "type": "REGISTER"
     * }
     *
     * @param request 验证码校验请求
     * @return 验证结果
     */
    @PostMapping("/verify")
    @Operation(summary = "验证邮箱验证码", description = "验证用户输入的邮箱验证码是否正确")
    public Result<Void> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        log.info("验证验证码请求: email={}, type={}", request.getEmail(), request.getType());

        verificationCodeService.verifyCode(
                request.getEmail(),
                request.getCode(),
                request.getType()
        );

        return Result.successMessage("验证码验证成功");
    }
}
