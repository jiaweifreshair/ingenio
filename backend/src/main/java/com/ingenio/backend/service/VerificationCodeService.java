package com.ingenio.backend.service;

import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.auth.VerificationType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 *
 * 功能：
 * 1. 生成6位数字验证码
 * 2. 存储到Redis（5分钟过期）
 * 3. 发送HTML格式邮件
 * 4. 防刷机制（60秒内限制1次）
 * 5. 验证码校验（验证后立即删除）
 *
 * Redis Key设计：
 * - verification:code:{type}:{email} = "123456" (TTL: 5分钟)
 * - verification:rate_limit:{email} = timestamp (TTL: 60秒)
 *
 * @author Ingenio Team
 * @since Phase 5.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final JavaMailSender mailSender;
    private final RedissonClient redissonClient;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int RATE_LIMIT_SECONDS = 60;
    private static final String CODE_KEY_PREFIX = "verification:code:";
    private static final String RATE_LIMIT_KEY_PREFIX = "verification:rate_limit:";

    /**
     * 发送验证码
     *
     * @param email 接收邮箱
     * @param type  验证码类型
     * @throws BusinessException 业务异常（防刷、发送失败等）
     */
    public void sendCode(String email, VerificationType type) {
        // 1. 检查防刷机制
        checkRateLimit(email);

        // 2. 生成6位随机数字验证码
        String code = generateCode();

        // 3. 存储到Redis（5分钟过期）
        String codeKey = buildCodeKey(type, email);
        RBucket<String> bucket = redissonClient.getBucket(codeKey);
        bucket.set(code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 4. 发送邮件
        try {
            sendEmail(email, code, type);
            log.info("验证码发送成功: email={}, type={}, code={}", email, type, code);
        } catch (MessagingException e) {
            log.error("验证码发送失败: email={}, type={}", email, type, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        // 5. 设置防刷标记（60秒内不能再次发送）
        String rateLimitKey = buildRateLimitKey(email);
        RBucket<Long> rateLimitBucket = redissonClient.getBucket(rateLimitKey);
        rateLimitBucket.set(System.currentTimeMillis(), RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 验证验证码
     *
     * @param email 邮箱
     * @param code  用户输入的验证码
     * @param type  验证码类型
     * @return true - 验证通过，false - 验证失败
     * @throws BusinessException 验证码错误或过期
     */
    public boolean verifyCode(String email, String code, VerificationType type) {
        // 1. 从Redis获取验证码
        String codeKey = buildCodeKey(type, email);
        RBucket<String> bucket = redissonClient.getBucket(codeKey);
        String storedCode = bucket.get();

        // 2. 验证码不存在或已过期
        if (storedCode == null) {
            log.warn("验证码不存在或已过期: email={}, type={}", email, type);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码已过期，请重新获取");
        }

        // 3. 验证码错误
        if (!storedCode.equals(code)) {
            log.warn("验证码错误: email={}, type={}, expected={}, actual={}", email, type, storedCode, code);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码错误");
        }

        // 4. 验证通过，立即删除验证码（防止重复使用）
        bucket.delete();
        log.info("验证码验证成功: email={}, type={}", email, type);
        return true;
    }

    /**
     * 检查防刷机制
     * 60秒内只能发送1次
     *
     * @param email 邮箱
     * @throws BusinessException 60秒内已发送过验证码
     */
    private void checkRateLimit(String email) {
        String rateLimitKey = buildRateLimitKey(email);
        RBucket<Long> bucket = redissonClient.getBucket(rateLimitKey);
        Long lastSendTime = bucket.get();

        if (lastSendTime != null) {
            long elapsedSeconds = (System.currentTimeMillis() - lastSendTime) / 1000;
            long remainingSeconds = RATE_LIMIT_SECONDS - elapsedSeconds;
            log.warn("发送验证码过于频繁: email={}, 剩余{}秒", email, remainingSeconds);
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    String.format("发送验证码过于频繁，请%d秒后重试", remainingSeconds)
            );
        }
    }

    /**
     * 生成6位随机数字验证码
     *
     * @return 6位数字验证码字符串
     */
    private String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 生成100000-999999之间的随机数
        return String.valueOf(code);
    }

    /**
     * 发送HTML格式邮件
     *
     * @param to   接收邮箱
     * @param code 验证码
     * @param type 验证码类型
     * @throws MessagingException 邮件发送异常
     */
    private void sendEmail(String to, String code, VerificationType type) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(buildEmailSubject(type));

        String htmlContent = buildEmailContent(code, type);
        helper.setText(htmlContent, true); // true表示HTML格式

        mailSender.send(message);
    }

    /**
     * 构建邮件主题
     *
     * @param type 验证码类型
     * @return 邮件主题
     */
    private String buildEmailSubject(VerificationType type) {
        return String.format("【秒构AI】%s验证码", type.getDescription());
    }

    /**
     * 构建邮件HTML内容
     *
     * @param code 验证码
     * @param type 验证码类型
     * @return HTML内容
     */
    private String buildEmailContent(String code, VerificationType type) {
        return String.format("""
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: Arial, 'Microsoft YaHei', sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; padding: 40px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .title { font-size: 24px; color: #333; margin-bottom: 10px; }
                        .subtitle { font-size: 14px; color: #666; }
                        .code-box { background-color: #f8f9fa; border: 2px dashed #dee2e6; border-radius: 6px; padding: 20px; text-align: center; margin: 30px 0; }
                        .code { font-size: 32px; font-weight: bold; color: #007bff; letter-spacing: 8px; }
                        .description { font-size: 14px; color: #666; line-height: 1.6; margin-bottom: 20px; }
                        .warning { font-size: 12px; color: #dc3545; margin-top: 20px; padding: 10px; background-color: #fff3cd; border-radius: 4px; }
                        .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #dee2e6; font-size: 12px; color: #999; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="title">秒构AI - %s</div>
                            <div class="subtitle">人人可用的AI应用生成器</div>
                        </div>

                        <div class="description">
                            您好，<br><br>
                            您正在进行<strong>%s</strong>操作，请使用以下验证码完成验证：
                        </div>

                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>

                        <div class="description">
                            验证码有效期为<strong>%d分钟</strong>，请尽快完成验证。
                        </div>

                        <div class="warning">
                            ⚠️ 安全提示：<br>
                            • 请勿将验证码告知他人<br>
                            • 如非本人操作，请忽略此邮件
                        </div>

                        <div class="footer">
                            此邮件由系统自动发送，请勿回复<br>
                            © 2025 秒构AI (Ingenio) - Made with ❤️ by Justin
                        </div>
                    </div>
                </body>
                </html>
                """,
                type.getDescription(),
                type.getDescription(),
                code,
                CODE_EXPIRE_MINUTES
        );
    }

    /**
     * 构建验证码Redis Key
     *
     * @param type  验证码类型
     * @param email 邮箱
     * @return Redis Key
     */
    private String buildCodeKey(VerificationType type, String email) {
        return CODE_KEY_PREFIX + type.getCode() + ":" + email;
    }

    /**
     * 构建防刷Redis Key
     *
     * @param email 邮箱
     * @return Redis Key
     */
    private String buildRateLimitKey(String email) {
        return RATE_LIMIT_KEY_PREFIX + email;
    }
}
