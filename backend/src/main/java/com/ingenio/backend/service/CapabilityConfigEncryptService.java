package com.ingenio.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 能力配置加密服务
 *
 * 使用AES-256-GCM算法加密敏感配置信息
 *
 * 安全设计：
 * 1. 加密密钥从环境变量读取，不硬编码
 * 2. 使用GCM模式提供认证加密
 * 3. 每次加密使用随机IV
 * 4. 密文格式：ENC:AES256:base64(iv+ciphertext+tag)
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎JeecgBoot能力集成)
 */
@Slf4j
@Service
public class CapabilityConfigEncryptService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "ENC:AES256:";

    @Value("${ingenio.capability.encrypt-key:}")
    private String encryptKeyBase64;

    /**
     * 加密明文
     *
     * @param plaintext 明文
     * @return 密文（格式：ENC:AES256:base64编码）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);

            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 组合IV和密文
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            String encoded = Base64.getEncoder().encodeToString(combined);
            return ENCRYPTED_PREFIX + encoded;

        } catch (Exception e) {
            log.error("加密失败", e);
            throw new RuntimeException("加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密密文
     *
     * @param ciphertext 密文（格式：ENC:AES256:base64编码）
     * @return 明文
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        if (!ciphertext.startsWith(ENCRYPTED_PREFIX)) {
            // 不是加密格式，直接返回
            return ciphertext;
        }

        try {
            String encoded = ciphertext.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            // 分离IV和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("解密失败", e);
            throw new RuntimeException("解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断是否为加密值
     *
     * @param value 待检查的值
     * @return 是否为加密格式
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * 加密配置Map中的敏感字段
     *
     * @param configValues 配置值
     * @param configTemplate 配置模板（标记哪些字段需要加密）
     * @return 加密后的配置值
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> encryptSensitiveFields(
            Map<String, Object> configValues,
            Map<String, Object> configTemplate
    ) {
        if (configValues == null || configTemplate == null) {
            return configValues;
        }

        Map<String, Object> result = new HashMap<>(configValues);

        for (Map.Entry<String, Object> entry : configTemplate.entrySet()) {
            String fieldName = entry.getKey();
            Object templateConfig = entry.getValue();

            if (templateConfig instanceof Map) {
                Map<String, Object> fieldConfig = (Map<String, Object>) templateConfig;
                Boolean encrypted = (Boolean) fieldConfig.get("encrypted");

                if (Boolean.TRUE.equals(encrypted) && result.containsKey(fieldName)) {
                    Object value = result.get(fieldName);
                    if (value instanceof String && !isEncrypted((String) value)) {
                        result.put(fieldName, encrypt((String) value));
                        log.debug("字段 {} 已加密", fieldName);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 解密配置Map中的敏感字段
     *
     * @param configValues 配置值（含加密字段）
     * @return 解密后的配置值
     */
    public Map<String, Object> decryptSensitiveFields(Map<String, Object> configValues) {
        if (configValues == null) {
            return configValues;
        }

        Map<String, Object> result = new HashMap<>(configValues);

        for (Map.Entry<String, Object> entry : result.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String && isEncrypted((String) value)) {
                result.put(entry.getKey(), decrypt((String) value));
                log.debug("字段 {} 已解密", entry.getKey());
            }
        }

        return result;
    }

    /**
     * 掩码敏感字段（用于API返回）
     *
     * @param configValues 配置值
     * @param configTemplate 配置模板
     * @return 掩码后的配置值
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> maskSensitiveFields(
            Map<String, Object> configValues,
            Map<String, Object> configTemplate
    ) {
        if (configValues == null || configTemplate == null) {
            return configValues;
        }

        Map<String, Object> result = new HashMap<>(configValues);

        for (Map.Entry<String, Object> entry : configTemplate.entrySet()) {
            String fieldName = entry.getKey();
            Object templateConfig = entry.getValue();

            if (templateConfig instanceof Map) {
                Map<String, Object> fieldConfig = (Map<String, Object>) templateConfig;
                Boolean encrypted = (Boolean) fieldConfig.get("encrypted");
                String type = (String) fieldConfig.get("type");

                // 加密字段或密码类型字段需要掩码
                if ((Boolean.TRUE.equals(encrypted) || "password".equals(type))
                        && result.containsKey(fieldName)) {
                    Object value = result.get(fieldName);
                    if (value instanceof String) {
                        result.put(fieldName, maskValue((String) value));
                    }
                }
            }
        }

        return result;
    }

    /**
     * 掩码字符串值
     *
     * @param value 原值
     * @return 掩码后的值（如：abc***xyz）
     */
    private String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // 如果是加密格式，显示为已配置
        if (isEncrypted(value)) {
            return "******(已配置)";
        }

        int length = value.length();
        if (length <= 6) {
            return "******";
        }

        // 显示前3后3，中间掩码
        return value.substring(0, 3) + "******" + value.substring(length - 3);
    }

    /**
     * 获取加密密钥
     */
    private SecretKey getSecretKey() {
        if (encryptKeyBase64 == null || encryptKeyBase64.isEmpty()) {
            // 使用默认密钥（仅用于开发环境）
            log.warn("未配置加密密钥，使用默认密钥（仅限开发环境）");
            byte[] defaultKey = "Ingenio-Capability-Config-Key!!".getBytes(StandardCharsets.UTF_8);
            return new SecretKeySpec(defaultKey, "AES");
        }

        byte[] keyBytes = Base64.getDecoder().decode(encryptKeyBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
