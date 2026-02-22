package com.ingenio.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Supabase配置（V2.0增强）
 *
 * 支持：
 * 1. Supabase Database（自动生成REST API）
 * 2. Supabase Auth（认证系统）
 * 3. Supabase Storage（文件存储）
 * 4. Supabase Realtime（WebSocket实时通信）
 * 5. 数据库直连（用于执行DDL - V2.0新增）
 *
 * 配置方式：
 * - 前期：使用Supabase商用版（https://supabase.com）
 * - 后期：切换到自托管开源版（Docker Compose）
 *
 * V2.0新增：
 * - directDatabaseUrl: 数据库直连URL，用于执行DDL
 * - dbPassword: 数据库密码
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-01-26
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "supabase")
public class SupabaseConfig {

    /**
     * Supabase项目URL
     * 商用版示例：https://xxxxx.supabase.co
     * 自托管示例：http://localhost:8000
     */
    private String url;

    /**
     * Supabase项目API Key（anon key）
     * 公开密钥，用于客户端调用
     */
    private String anonKey;

    /**
     * Supabase服务角色密钥（service_role key）
     * 私密密钥，拥有完全权限，仅用于服务端
     */
    private String serviceRoleKey;

    /**
     * 数据库直连URL（V2.0新增）
     *
     * 用途：执行DDL语句（CREATE TABLE等）
     * 格式：postgresql://postgres.[project-ref]:[password]@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres
     *
     * 获取方式：
     * 1. Supabase Dashboard > Settings > Database
     * 2. 复制 Connection string > JDBC 格式
     */
    private String directDatabaseUrl;

    /**
     * 数据库密码（V2.0新增）
     *
     * 获取方式：
     * 1. Supabase Dashboard > Settings > Database
     * 2. 点击 "Reset database password" 或使用已设置的密码
     */
    private String dbPassword;

    /**
     * Database REST API URL
     * 格式：{url}/rest/v1
     */
    public String getDatabaseUrl() {
        return url + "/rest/v1";
    }

    /**
     * Auth API URL
     * 格式：{url}/auth/v1
     */
    public String getAuthUrl() {
        return url + "/auth/v1";
    }

    /**
     * Storage API URL
     * 格式：{url}/storage/v1
     */
    public String getStorageUrl() {
        return url + "/storage/v1";
    }

    /**
     * Realtime WebSocket URL
     * 格式：{url}/realtime/v1
     */
    public String getRealtimeUrl() {
        return url + "/realtime/v1";
    }

    /**
     * 获取JDBC格式的数据库直连URL（V2.0新增）
     *
     * 优先使用配置的 directDatabaseUrl，如果未配置则根据 url 和 dbPassword 构建
     *
     * @return JDBC格式的数据库连接URL
     */
    public String getJdbcDirectUrl() {
        // 优先使用显式配置的直连URL
        if (StringUtils.hasText(directDatabaseUrl)) {
            return directDatabaseUrl;
        }

        // 根据项目URL推断直连URL（适用于 Supabase 云服务）
        // 项目URL格式：https://xxx.supabase.co
        // 直连URL格式：jdbc:postgresql://db.xxx.supabase.co:5432/postgres
        if (StringUtils.hasText(url) && StringUtils.hasText(dbPassword)) {
            String projectRef = extractProjectRef(url);
            if (projectRef != null) {
                return String.format(
                    "jdbc:postgresql://db.%s.supabase.co:5432/postgres?user=postgres&password=%s",
                    projectRef,
                    dbPassword
                );
            }
        }

        return null;
    }

    /**
     * 从项目URL中提取项目引用ID
     *
     * @param projectUrl 项目URL（如 https://xxyfiwhvdriqfcmbbbsy.supabase.co）
     * @return 项目引用ID（如 xxyfiwhvdriqfcmbbbsy）
     */
    private String extractProjectRef(String projectUrl) {
        if (!StringUtils.hasText(projectUrl)) {
            return null;
        }
        // 移除协议前缀
        String host = projectUrl.replaceAll("^https?://", "");
        // 提取子域名（项目引用ID）
        int dotIndex = host.indexOf('.');
        if (dotIndex > 0) {
            return host.substring(0, dotIndex);
        }
        return null;
    }

    /**
     * 检查Supabase是否已配置
     */
    public boolean isConfigured() {
        return url != null && !url.isEmpty() &&
               (anonKey != null && !anonKey.isEmpty() ||
                serviceRoleKey != null && !serviceRoleKey.isEmpty());
    }

    /**
     * 检查是否可以执行DDL（V2.0新增）
     *
     * @return true 如果配置了数据库直连凭据
     */
    public boolean canExecuteDdl() {
        return StringUtils.hasText(directDatabaseUrl) ||
               (StringUtils.hasText(url) && StringUtils.hasText(dbPassword));
    }
}
