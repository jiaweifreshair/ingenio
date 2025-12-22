package com.ingenio.backend.service;

import java.time.Instant;
import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.annotation.RequireOwnership;
import com.ingenio.backend.dto.user.ApiKeyResponse;
import com.ingenio.backend.dto.user.CreateApiKeyRequest;
import com.ingenio.backend.entity.ApiKeyEntity;
import com.ingenio.backend.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API密钥管理服务
 * 提供API密钥的创建、查询、删除等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyManagementService {

    private final ApiKeyMapper apiKeyMapper;

    // API密钥前缀
    private static final String API_KEY_PREFIX = "ing_";

    // API密钥长度（字节数）
    private static final int API_KEY_LENGTH = 32;

    /**
     * 生成API密钥
     *
     * 功能：
     * 1. 生成唯一的API密钥（ing_xxxxxx格式）
     * 2. 使用SHA256哈希存储
     * 3. 设置权限范围和速率限制
     * 4. 设置过期时间（可选）
     *
     * @param request 创建请求
     * @return API密钥响应（包含完整密钥，仅此一次）
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyResponse generateApiKey(CreateApiKeyRequest request) {
        String userId = StpUtil.getLoginIdAsString();

        // 检查用户是否已登录
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("生成API密钥失败：用户未登录");
            throw new RuntimeException("用户未登录，无法生成API密钥");
        }

        // 1. 生成原始密钥
        String rawKey = generateRawApiKey();
        String fullKey = API_KEY_PREFIX + rawKey;

        // 2. 计算密钥哈希值（SHA256）
        String keyHash = hashApiKey(rawKey);

        // 3. 生成密钥前缀（显示用）
        String keyPrefix = API_KEY_PREFIX + rawKey.substring(0, 8);

        // 4. 计算过期时间
        Instant expiresAt = null;
        if (request.getExpireDays() != null && request.getExpireDays() > 0) {
            expiresAt = Instant.now().plus(java.time.Duration.ofDays(request.getExpireDays()));
        }

        // 5. 创建密钥实体
        ApiKeyEntity apiKey = ApiKeyEntity.builder()
                .userId(UUID.fromString(userId))
                .name(request.getName())
                .keyValue(keyHash)
                .keyPrefix(keyPrefix)
                .description(request.getDescription())
                .scopes(request.getScopes())
                .isActive(true)
                .usageCount(0)
                .rateLimit(request.getRateLimit())
                .expiresAt(expiresAt)
                .build();

        int inserted = apiKeyMapper.insert(apiKey);
        if (inserted == 0) {
            throw new RuntimeException("创建API密钥失败");
        }

        log.info("API密钥创建成功: userId={}, keyId={}, keyPrefix={}", userId, apiKey.getId(), keyPrefix);

        // 6. 构造响应（包含完整密钥，仅此一次返回）
        return ApiKeyResponse.builder()
                .id(apiKey.getId().toString())
                .name(apiKey.getName())
                .keyPrefix(keyPrefix)
                .fullKey(fullKey) // 完整密钥仅返回一次
                .description(apiKey.getDescription())
                .scopes(apiKey.getScopes())
                .isActive(apiKey.getIsActive())
                .usageCount(apiKey.getUsageCount())
                .rateLimit(apiKey.getRateLimit())
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .build();
    }

    /**
     * 获取用户所有API密钥
     *
     * @return API密钥列表（不包含完整密钥）
     */
    public List<ApiKeyResponse> listApiKeys() {
        String userId = StpUtil.getLoginIdAsString();

        // 检查用户是否已登录
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("获取API密钥列表失败：用户未登录");
            throw new RuntimeException("用户未登录，无法获取API密钥列表");
        }

        List<ApiKeyEntity> apiKeys = apiKeyMapper.findByUserId(UUID.fromString(userId));

        return apiKeys.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 删除API密钥
     *
     * @param keyId 密钥ID
     */
    @RequireOwnership(resourceType = "api_key", idParam = "keyId")
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(String keyId) {
        String userId = StpUtil.getLoginIdAsString();

        // 检查用户是否已登录
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("删除API密钥失败：用户未登录");
            throw new RuntimeException("用户未登录，无法删除API密钥");
        }

        int deleted = apiKeyMapper.deleteByIdAndUserId(UUID.fromString(keyId), UUID.fromString(userId));
        if (deleted == 0) {
            throw new RuntimeException("删除API密钥失败，密钥不存在或无权限");
        }

        log.info("API密钥删除成功: userId={}, keyId={}", userId, keyId);
    }

    /**
     * 验证API密钥
     *
     * 功能：
     * 1. 查找密钥
     * 2. 验证哈希值
     * 3. 检查是否启用
     * 4. 检查是否过期
     * 5. 更新最后使用时间和使用次数
     *
     * @param apiKey 完整API密钥
     * @param ip     客户端IP
     * @return API密钥实体（验证成功），null（验证失败）
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyEntity verifyApiKey(String apiKey, String ip) {
        // 1. 验证格式
        if (apiKey == null || !apiKey.startsWith(API_KEY_PREFIX)) {
            log.warn("无效的API密钥格式: {}", apiKey);
            return null;
        }

        // 2. 提取原始密钥部分
        String rawKey = apiKey.substring(API_KEY_PREFIX.length());

        // 3. 计算哈希值
        String keyHash = hashApiKey(rawKey);

        // 4. 查找密钥
        ApiKeyEntity keyEntity = apiKeyMapper.findByKeyValue(keyHash).orElse(null);

        if (keyEntity == null) {
            log.warn("API密钥不存在: keyHash={}", keyHash);
            return null;
        }

        // 5. 验证密钥有效性
        if (!keyEntity.isValid()) {
            log.warn("API密钥无效: keyId={}, isActive={}, expiresAt={}",
                    keyEntity.getId(), keyEntity.getIsActive(), keyEntity.getExpiresAt());
            return null;
        }

        // 6. 更新最后使用时间和使用次数
        apiKeyMapper.updateLastUsed(keyEntity.getId(), ip);

        log.debug("API密钥验证成功: keyId={}, userId={}", keyEntity.getId(), keyEntity.getUserId());

        return keyEntity;
    }

    /**
     * 生成原始API密钥（不包含前缀）
     *
     * @return 随机字符串
     */
    private String generateRawApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[API_KEY_LENGTH];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 使用SHA256哈希API密钥
     *
     * @param rawKey 原始密钥
     * @return 哈希值（十六进制字符串）
     */
    private String hashApiKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    /**
     * 将ApiKeyEntity转换为ApiKeyResponse
     */
    private ApiKeyResponse convertToResponse(ApiKeyEntity entity) {
        return ApiKeyResponse.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .keyPrefix(entity.getKeyPrefix())
                .description(entity.getDescription())
                .scopes(entity.getScopes())
                .isActive(entity.getIsActive())
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedIp(entity.getLastUsedIp())
                .usageCount(entity.getUsageCount())
                .rateLimit(entity.getRateLimit())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
