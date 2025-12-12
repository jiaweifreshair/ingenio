package com.ingenio.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.agent.dto.ValidateResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 完整生成流程响应DTO
 * 包含生成的AppSpec、代码下载URL、质量评分等信息
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateFullResponse {

    /**
     * 生成的AppSpec ID
     */
    private UUID appSpecId;

    /**
     * 生成的项目ID（如果自动创建了项目）
     */
    private UUID projectId;

    /**
     * 规划结果（PlanAgent输出）
     */
    private PlanResult planResult;

    /**
     * 验证结果（ValidateAgent输出）
     */
    private ValidateResult validateResult;

    /**
     * 是否验证通过
     */
    private Boolean isValid;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 生成的代码下载URL
     * 指向MinIO存储的ZIP文件
     */
    private String codeDownloadUrl;

    /**
     * 代码生成摘要
     * 包含生成的文件统计信息
     */
    private CodeGenerationSummary codeSummary;

    /**
     * 生成的文件清单
     * 列出所有生成的文件路径
     */
    private java.util.List<String> generatedFileList;

    /**
     * 在线预览URL
     * 指向部署的预览环境
     */
    private String previewUrl;

    /**
     * 生成状态：planning/executing/validating/generating/completed/failed
     */
    private String status;

    /**
     * 错误消息（如果失败）
     */
    private String errorMessage;

    /**
     * 生成耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 生成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant generatedAt;

    /**
     * Token使用统计
     */
    private TokenUsage tokenUsage;

    /**
     * Token使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {

        /**
         * PlanAgent使用的Token数
         */
        private Integer planTokens;

        /**
         * ExecuteAgent使用的Token数
         */
        private Integer executeTokens;

        /**
         * ValidateAgent使用的Token数
         */
        private Integer validateTokens;

        /**
         * 总Token数
         */
        private Integer totalTokens;

        /**
         * 预估成本（美元）
         */
        private Double estimatedCost;
    }

    /**
     * 代码生成摘要
     * 包含生成的文件统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeGenerationSummary {

        /**
         * 总文件数
         */
        private Integer totalFiles;

        /**
         * 数据库Schema文件数
         */
        private Integer databaseSchemaFiles;

        /**
         * 数据模型文件数（data class）
         */
        private Integer dataModelFiles;

        /**
         * Repository文件数
         */
        private Integer repositoryFiles;

        /**
         * ViewModel文件数
         */
        private Integer viewModelFiles;

        /**
         * UI界面文件数
         */
        private Integer uiScreenFiles;

        /**
         * AI集成文件数（新增）
         */
        private Integer aiIntegrationFiles;

        /**
         * 配置文件数
         */
        private Integer configFiles;

        /**
         * 文档文件数
         */
        private Integer documentFiles;

        /**
         * 总文件大小（字节）
         */
        private Long totalSize;

        /**
         * ZIP文件名
         */
        private String zipFileName;
    }
}
