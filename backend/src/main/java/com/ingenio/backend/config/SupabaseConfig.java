package com.ingenio.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Supabase配置
 *
 * 支持：
 * 1. Supabase Database（自动生成REST API）
 * 2. Supabase Auth（认证系统）
 * 3. Supabase Storage（文件存储）
 * 4. Supabase Realtime（WebSocket实时通信）
 *
 * 配置方式：
 * - 前期：使用Supabase商用版（https://supabase.com）
 * - 后期：切换到自托管开源版（Docker Compose）
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
     * 检查Supabase是否已配置
     */
    public boolean isConfigured() {
        return url != null && !url.isEmpty() &&
               (anonKey != null && !anonKey.isEmpty() ||
                serviceRoleKey != null && !serviceRoleKey.isEmpty());
    }
}
