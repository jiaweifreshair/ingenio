package com.ingenio.backend.common.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务ID生成器
 * 参考pulse-hive项目的UUID生成策略，生成具有业务含义的ID
 *
 * 格式：前缀_时间戳_随机数_UUID后缀
 * 例如：USER_20251105_12345678_abc123
 */
public class IdGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 生成用户ID
     * 格式：USER_20251105_random_UUID后8位
     */
    public static String generateUserId() {
        return generateId("USER");
    }

    /**
     * 生成项目ID
     * 格式：PRJ_20251105_random_UUID后8位
     */
    public static String generateProjectId() {
        return generateId("PRJ");
    }

    /**
     * 生成AppSpec ID
     * 格式：SPEC_20251105_random_UUID后8位
     */
    public static String generateAppSpecId() {
        return generateId("SPEC");
    }

    /**
     * 生成AppSpec版本ID
     * 格式：VER_20251105_random_UUID后8位
     */
    public static String generateVersionId() {
        return generateId("VER");
    }

    /**
     * 生成代码ID
     * 格式：CODE_20251105_random_UUID后8位
     */
    public static String generateCodeId() {
        return generateId("CODE");
    }

    /**
     * 生成Fork ID
     * 格式：FORK_20251105_random_UUID后8位
     */
    public static String generateForkId() {
        return generateId("FORK");
    }

    /**
     * 生成社交互动ID
     * 格式：SOCIAL_20251105_random_UUID后8位
     */
    public static String generateSocialId() {
        return generateId("SOCIAL");
    }

    /**
     * 生成魔法提示词ID
     * 格式：MAGIC_20251105_random_UUID后8位
     */
    public static String generateMagicPromptId() {
        return generateId("MAGIC");
    }

    /**
     * 生成租户ID
     * 格式：TENANT_20251105_random_UUID后8位
     */
    public static String generateTenantId() {
        return generateId("TENANT");
    }

    /**
     * 通用ID生成器
     * 格式：前缀_日期_随机数_UUID后8位
     */
    public static String generateId(String prefix) {
        String date = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(DATE_FORMATTER);
        String randomNum = String.format("%08d", ThreadLocalRandom.current().nextInt(100000000));
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(24);

        return String.format("%s_%s_%s_%s", prefix, date, randomNum, uuidSuffix);
    }

    /**
     * 从业务ID中提取前缀
     */
    public static String extractPrefix(String businessId) {
        if (businessId == null || businessId.isEmpty()) {
            return "";
        }
        int firstUnderscore = businessId.indexOf('_');
        return firstUnderscore > 0 ? businessId.substring(0, firstUnderscore) : businessId;
    }

    /**
     * 验证是否为有效的业务ID
     */
    public static boolean isValidBusinessId(String businessId) {
        if (businessId == null || businessId.isEmpty()) {
            return false;
        }

        String[] parts = businessId.split("_");
        return parts.length == 4 &&
               parts[0].matches("^[A-Z]+$") &&           // 前缀必须是全大写字母
               parts[1].matches("^\\d{8}$") &&           // 日期必须是8位数字
               parts[2].matches("^\\d{8}$") &&           // 随机数必须是8位数字
               parts[3].matches("^[a-f0-9]{8}$");        // UUID后缀必须是8位十六进制
    }
}
