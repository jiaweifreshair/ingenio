package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 代码生成结果
 *
 * ExecuteAgent生成代码后的完整输出：
 * - 生成的文件列表
 * - 项目结构
 * - 构建配置
 * - 依赖清单
 */
@Data
@Builder
public class CodeGenerationResult {

    /**
     * 任务ID
     */
    private UUID taskId;

    /**
     * 租户ID
     */
    private UUID tenantId;

    /**
     * 项目根目录
     */
    private String projectRoot;

    /**
     * 生成的文件列表
     */
    private List<GeneratedFile> files;

    /**
     * 项目类型
     * kmp / spring-boot / nextjs
     */
    private String projectType;

    /**
     * 技术栈
     */
    private String techStack;

    /**
     * 构建配置文件内容
     * build.gradle.kts / pom.xml / package.json
     */
    private Map<String, String> buildConfigs;

    /**
     * 依赖清单
     */
    private List<Dependency> dependencies;

    /**
     * 环境变量配置
     */
    private Map<String, String> environmentVariables;

    /**
     * 数据库Schema
     */
    private String databaseSchema;

    /**
     * Supabase配置
     */
    private SupabaseConfig supabaseConfig;

    /**
     * 生成的文件
     */
    @Data
    @Builder
    public static class GeneratedFile {
        /**
         * 文件路径（相对于项目根目录）
         */
        private String path;

        /**
         * 文件内容
         */
        private String content;

        /**
         * 文件类型
         * kotlin / java / typescript / xml
         */
        private String type;

        /**
         * 文件大小（字节）
         */
        private Long size;
    }

    /**
     * 依赖信息
     */
    @Data
    @Builder
    public static class Dependency {
        /**
         * 依赖坐标
         * groupId:artifactId (Maven) 或 package-name (npm)
         */
        private String coordinate;

        /**
         * 版本号
         */
        private String version;

        /**
         * 依赖作用域
         * implementation / compileOnly / testImplementation
         */
        private String scope;
    }

    /**
     * Supabase配置
     */
    @Data
    @Builder
    public static class SupabaseConfig {
        /**
         * Supabase项目URL
         */
        private String url;

        /**
         * Supabase API密钥
         */
        private String apiKey;

        /**
         * 是否使用匿名访问
         */
        private Boolean anonymous;
    }

    /**
     * 获取文件总数
     */
    public int getFileCount() {
        return files != null ? files.size() : 0;
    }

    /**
     * 获取代码总行数
     */
    public int getTotalLines() {
        if (files == null) {
            return 0;
        }
        return files.stream()
                .mapToInt(f -> f.getContent().split("\n").length)
                .sum();
    }
}
