package com.ingenio.backend.service;

import java.time.Instant;
import java.time.Instant;
import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.entity.PasswordResetTokenEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.PasswordResetTokenMapper;
import com.ingenio.backend.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 密码重置服务
 * 提供密码重置的完整流程：创建令牌、验证令牌、重置密码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenMapper resetTokenMapper;
    private final UserMapper userMapper;
    private final HttpServletRequest request;

    // 令牌有效期：1小时
    private static final int TOKEN_EXPIRY_HOURS = 1;

    /**
     * 创建密码重置令牌
     *
     * 功能：
     * 1. 验证用户邮箱是否存在
     * 2. 生成64位随机令牌
     * 3. 保存到数据库（有效期1小时）
     * 4. 发送重置邮件（TODO: 集成邮件服务）
     *
     * @param email 用户邮箱
     * @return 重置令牌（用于测试，生产环境不返回）
     */
    @Transactional(rollbackFor = Exception.class)
    public String createResetToken(String email) {
        // 1. 查找用户
        UserEntity user = userMapper.selectOne(
                new QueryWrapper<UserEntity>().eq("email", email)
        );

        if (user == null) {
            // 安全考虑：不暴露用户是否存在，始终返回成功
            log.warn("尝试为不存在的邮箱创建重置令牌: {}", email);
            return null;
        }

        // 2. 生成随机令牌（64位）
        String token = generateSecureToken();

        // 3. 创建令牌记录
        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(Instant.now().plus(java.time.Duration.ofHours(TOKEN_EXPIRY_HOURS)))
                .used(false)
                .ipAddress(getClientIp())
                .userAgent(request.getHeader("User-Agent"))
                .build();

        // 使用自定义的insertWithCast方法解决UUID类型匹配问题
        int inserted = resetTokenMapper.insertWithCast(resetToken);
        if (inserted == 0) {
            throw new RuntimeException("创建密码重置令牌失败");
        }

        log.info("创建密码重置令牌成功: userId={}, email={}", user.getId(), email);

        // 4. TODO: 发送重置邮件
        // emailService.sendPasswordResetEmail(email, token);

        return token; // 生产环境应该返回null
    }

    /**
     * 验证重置令牌是否有效
     *
     * @param token 重置令牌
     * @return 是否有效
     */
    public boolean verifyResetToken(String token) {
        PasswordResetTokenEntity resetToken = resetTokenMapper.findByToken(token).orElse(null);

        if (resetToken == null) {
            log.warn("令牌不存在: {}", token);
            return false;
        }

        boolean isValid = resetToken.isValid();
        log.debug("验证重置令牌: token={}, isValid={}", token, isValid);

        return isValid;
    }

    /**
     * 重置密码
     *
     * 功能：
     * 1. 验证令牌有效性
     * 2. 验证密码强度
     * 3. 更新用户密码
     * 4. 标记令牌为已使用
     * 5. 清除用户所有未使用的令牌
     *
     * @param token          重置令牌
     * @param newPassword    新密码
     * @param confirmPassword 确认密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        // 1. 验证密码一致性
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 2. 查找并验证令牌
        PasswordResetTokenEntity resetToken = resetTokenMapper.findByToken(token)
                .orElseThrow(() -> new RuntimeException("无效的重置令牌"));

        if (!resetToken.isValid()) {
            throw new RuntimeException("重置令牌已过期或已使用");
        }

        // 3. 更新用户密码
        UserEntity user = userMapper.findByIdWithCast(resetToken.getUserId().toString())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setPasswordHash(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(Instant.now());

        int updated = userMapper.updateByIdWithCast(user);
        if (updated == 0) {
            throw new RuntimeException("密码重置失败");
        }

        // 4. 标记令牌为已使用
        resetToken.markAsUsed();
        resetTokenMapper.updateByIdWithCast(resetToken);

        // 5. 清除用户所有未使用的令牌（防止重复使用）
        resetTokenMapper.markAllAsUsedByUserIdWithCast(user.getId());

        log.info("密码重置成功: userId={}, email={}", user.getId(), user.getEmail());
    }

    /**
     * 生成安全的随机令牌（64位）
     *
     * @return Base64编码的随机字符串
     */
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[48]; // 48字节 = 64个Base64字符
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 获取客户端IP地址
     *
     * @return IP地址
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个代理的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 清理过期的令牌（定时任务调用）
     *
     * @return 删除的令牌数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredTokens() {
        int deleted = resetTokenMapper.deleteExpiredTokens();
        log.info("清理过期的密码重置令牌: 删除{}个", deleted);
        return deleted;
    }
}
