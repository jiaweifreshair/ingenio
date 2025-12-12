package com.ingenio.backend.dto.nl2backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * NL2Backend请求（统一请求DTO）
 *
 * 用户通过自然语言描述需求，系统自动生成后端服务
 */
@Data
public class NL2BackendRequest {

    /**
     * 自然语言需求描述
     * 示例：
     * "我需要一个博客系统，包含文章、评论、标签功能。
     *  文章有标题、内容、作者、发布时间。
     *  评论属于某篇文章，包含评论内容、评论人、评论时间。
     *  标签可以关联多篇文章。"
     */
    @NotBlank(message = "需求描述不能为空")
    @Size(min = 20, max = 10000, message = "需求描述长度必须在20-10000之间")
    private String requirement;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 生成选项
     */
    private GenerationOptions options;

    /**
     * 生成选项
     */
    @Data
    public static class GenerationOptions {
        /**
         * 是否生成认证授权
         */
        private Boolean includeAuth = false;

        /**
         * 是否生成测试代码
         */
        private Boolean includeTests = true;

        /**
         * 是否生成前端代码
         */
        private Boolean generateFrontend = false;

        /**
         * 目标数据库（postgresql/mysql/sqlite）
         */
        private String targetDatabase = "postgresql";

        /**
         * 代码风格（standard/clean/ddd）
         */
        private String codeStyle = "standard";
    }
}
