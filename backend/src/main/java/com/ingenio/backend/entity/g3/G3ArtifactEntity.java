package com.ingenio.backend.entity.g3;

import com.baomidou.mybatisplus.annotation.*;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * G3引擎产物实体类
 * 存储每轮生成的代码文件及其验证状态
 *
 * 产物类型包括：
 * - CONTRACT: OpenAPI契约文件
 * - ENTITY: 实体类
 * - MAPPER: MyBatis Mapper
 * - SERVICE: 服务层
 * - CONTROLLER: 控制器
 * - CONFIG: 配置文件
 * - TEST: 测试文件
 * - FRONTEND: 前端组件
 */
@Data
@Builder
@NoArgsConstructor
@TableName(value = "g3_artifacts", autoResultMap = true)
public class G3ArtifactEntity {

    /**
     * 产物ID（UUID主键）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    /**
     * 关联的G3任务ID
     */
    @TableField(value = "job_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID jobId;

    /**
     * 产物类型
     * 
     * @see ArtifactType
     */
    @TableField("artifact_type")
    private String artifactType;

    /**
     * 文件相对路径
     * 如：src/main/java/com/example/entity/User.java
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件名
     * 如：User.java
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件内容（完整代码）
     */
    @TableField("content")
    private String content;

    /**
     * 编程语言
     * 
     * @see Language
     */
    @TableField("language")
    private String language;

    /**
     * 产物版本号
     * 首次生成为1，每次Coach修复后版本号+1
     */
    @TableField("version")
    private Integer version;

    /**
     * 内容SHA256校验和
     */
    @TableField("checksum")
    private String checksum;

    /**
     * 父版本产物ID（用于追溯修复历史）
     */
    @TableField(value = "parent_artifact_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID parentArtifactId;

    /**
     * 是否有编译/运行错误
     */
    @TableField("has_errors")
    private Boolean hasErrors;

    /**
     * 编译器输出（错误信息）
     */
    @TableField("compiler_output")
    private String compilerOutput;

    /**
     * 验证时间
     */
    @TableField("validated_at")
    private Instant validatedAt;

    /**
     * 生成者Agent
     * 
     * @see GeneratedBy
     */
    @TableField("generated_by")
    private String generatedBy;

    /**
     * 生成轮次（0=首次，>0=修复轮次）
     */
    @TableField("generation_round")
    private Integer generationRound;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    // Manual Boilerplate for Lombok Failure
    public G3ArtifactEntity(UUID id, UUID jobId, String artifactType, String filePath, String fileName, String content,
            String language, Integer version, String checksum, UUID parentArtifactId, Boolean hasErrors,
            String compilerOutput, Instant validatedAt, String generatedBy, Integer generationRound, Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.jobId = jobId;
        this.artifactType = artifactType;
        this.filePath = filePath;
        this.fileName = fileName;
        this.content = content;
        this.language = language;
        this.version = version;
        this.checksum = checksum;
        this.parentArtifactId = parentArtifactId;
        this.hasErrors = hasErrors;
        this.compilerOutput = compilerOutput;
        this.validatedAt = validatedAt;
        this.generatedBy = generatedBy;
        this.generationRound = generationRound;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static G3ArtifactEntityBuilder builder() {
        return new G3ArtifactEntityBuilder();
    }

    public static class G3ArtifactEntityBuilder {
        private UUID id;
        private UUID jobId;
        private String artifactType;
        private String filePath;
        private String fileName;
        private String content;
        private String language;
        private Integer version;
        private String checksum;
        private UUID parentArtifactId;
        private Boolean hasErrors;
        private String compilerOutput;
        private Instant validatedAt;
        private String generatedBy;
        private Integer generationRound;
        private Instant createdAt;
        private Instant updatedAt;

        G3ArtifactEntityBuilder() {
        }

        public G3ArtifactEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public G3ArtifactEntityBuilder jobId(UUID jobId) {
            this.jobId = jobId;
            return this;
        }

        public G3ArtifactEntityBuilder artifactType(String artifactType) {
            this.artifactType = artifactType;
            return this;
        }

        public G3ArtifactEntityBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public G3ArtifactEntityBuilder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public G3ArtifactEntityBuilder content(String content) {
            this.content = content;
            return this;
        }

        public G3ArtifactEntityBuilder language(String language) {
            this.language = language;
            return this;
        }

        public G3ArtifactEntityBuilder version(Integer version) {
            this.version = version;
            return this;
        }

        public G3ArtifactEntityBuilder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public G3ArtifactEntityBuilder parentArtifactId(UUID parentArtifactId) {
            this.parentArtifactId = parentArtifactId;
            return this;
        }

        public G3ArtifactEntityBuilder hasErrors(Boolean hasErrors) {
            this.hasErrors = hasErrors;
            return this;
        }

        public G3ArtifactEntityBuilder compilerOutput(String compilerOutput) {
            this.compilerOutput = compilerOutput;
            return this;
        }

        public G3ArtifactEntityBuilder validatedAt(Instant validatedAt) {
            this.validatedAt = validatedAt;
            return this;
        }

        public G3ArtifactEntityBuilder generatedBy(String generatedBy) {
            this.generatedBy = generatedBy;
            return this;
        }

        public G3ArtifactEntityBuilder generationRound(Integer generationRound) {
            this.generationRound = generationRound;
            return this;
        }

        public G3ArtifactEntity build() {
            return new G3ArtifactEntity(id, jobId, artifactType, filePath, fileName, content, language, version,
                    checksum, parentArtifactId, hasErrors, compilerOutput, validatedAt, generatedBy, generationRound,
                    createdAt, updatedAt);
        }
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContent() {
        return content;
    }

    public String getLanguage() {
        return language;
    }

    public Integer getVersion() {
        return version;
    }

    public String getChecksum() {
        return checksum;
    }

    public UUID getParentArtifactId() {
        return parentArtifactId;
    }

    public Boolean getHasErrors() {
        return hasErrors;
    }

    public String getCompilerOutput() {
        return compilerOutput;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public Integer getGenerationRound() {
        return generationRound;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setters (if needed)
    public void setHasErrors(Boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public void setCompilerOutput(String compilerOutput) {
        this.compilerOutput = compilerOutput;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    /**
     * 产物类型枚举
     */
    public enum ArtifactType {
        CONTRACT("CONTRACT", "OpenAPI契约"),
        SCHEMA("SCHEMA", "数据库Schema"),
        ENTITY("ENTITY", "实体类"),
        MAPPER("MAPPER", "MyBatis Mapper"),
        SERVICE("SERVICE", "服务层"),
        CONTROLLER("CONTROLLER", "控制器"),
        CONFIG("CONFIG", "配置文件"),
        TEST("TEST", "测试文件"),
        FRONTEND("FRONTEND", "前端组件"),
        OTHER("OTHER", "其他");

        private final String value;
        private final String description;

        ArtifactType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static ArtifactType fromValue(String value) {
            for (ArtifactType type : ArtifactType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return OTHER;
        }

        /**
         * 根据文件路径推断产物类型
         */
        public static ArtifactType fromFilePath(String filePath) {
            if (filePath == null)
                return OTHER;

            String lowerPath = filePath.toLowerCase();
            if (lowerPath.contains("openapi") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
                return CONTRACT;
            }
            if (lowerPath.contains("schema") && lowerPath.endsWith(".sql")) {
                return SCHEMA;
            }
            if (lowerPath.contains("/entity/") || lowerPath.contains("/model/") || lowerPath.contains("/domain/")) {
                return ENTITY;
            }
            if (lowerPath.contains("/mapper/") || lowerPath.contains("/repository/") || lowerPath.contains("/dao/")) {
                return MAPPER;
            }
            if (lowerPath.contains("/service/")) {
                return SERVICE;
            }
            if (lowerPath.contains("/controller/") || lowerPath.contains("/rest/") || lowerPath.contains("/api/")) {
                return CONTROLLER;
            }
            if (lowerPath.contains("/config/") || lowerPath.contains("application")) {
                return CONFIG;
            }
            if (lowerPath.contains("/test/") || lowerPath.contains("test.java") || lowerPath.contains(".spec.")) {
                return TEST;
            }
            if (lowerPath.endsWith(".tsx") || lowerPath.endsWith(".jsx") || lowerPath.contains("/components/")) {
                return FRONTEND;
            }
            return OTHER;
        }
    }

    /**
     * 编程语言枚举
     */
    public enum Language {
        JAVA("java", "Java"),
        TYPESCRIPT("typescript", "TypeScript"),
        JAVASCRIPT("javascript", "JavaScript"),
        SQL("sql", "SQL"),
        YAML("yaml", "YAML"),
        XML("xml", "XML"),
        JSON("json", "JSON"),
        PROPERTIES("properties", "Properties");

        private final String value;
        private final String description;

        Language(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 根据文件扩展名推断语言
         */
        public static Language fromFileName(String fileName) {
            if (fileName == null)
                return JAVA;

            String lowerName = fileName.toLowerCase();
            if (lowerName.endsWith(".java"))
                return JAVA;
            if (lowerName.endsWith(".ts") || lowerName.endsWith(".tsx"))
                return TYPESCRIPT;
            if (lowerName.endsWith(".js") || lowerName.endsWith(".jsx"))
                return JAVASCRIPT;
            if (lowerName.endsWith(".sql"))
                return SQL;
            if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml"))
                return YAML;
            if (lowerName.endsWith(".xml"))
                return XML;
            if (lowerName.endsWith(".json"))
                return JSON;
            if (lowerName.endsWith(".properties"))
                return PROPERTIES;
            return JAVA;
        }
    }

    /**
     * 生成者枚举
     */
    public enum GeneratedBy {
        ARCHITECT("ARCHITECT", "架构师Agent"),
        BACKEND_CODER("BACKEND_CODER", "后端编码器"),
        FRONTEND_CODER("FRONTEND_CODER", "前端编码器"),
        COACH("COACH", "修复教练");

        private final String value;
        private final String description;

        GeneratedBy(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 标记产物有编译错误
     */
    public void markError(String compilerOutput) {
        this.hasErrors = true;
        this.compilerOutput = compilerOutput;
        this.validatedAt = Instant.now();
    }

    /**
     * 标记产物验证通过
     */
    public void markValid() {
        this.hasErrors = false;
        this.compilerOutput = null;
        this.validatedAt = Instant.now();
    }

    /**
     * 创建新版本（用于Coach修复）
     */
    public G3ArtifactEntity createNewVersion(String newContent, String generatedBy) {
        return G3ArtifactEntity.builder()
                .id(UUID.randomUUID())
                .jobId(this.jobId)
                .artifactType(this.artifactType)
                .filePath(this.filePath)
                .fileName(this.fileName)
                .content(newContent)
                .language(this.language)
                .version(this.version + 1)
                .parentArtifactId(this.id)
                .hasErrors(false)
                .generatedBy(generatedBy)
                .generationRound(this.generationRound + 1)
                .build();
    }

    /**
     * 创建新的G3产物
     */
    public static G3ArtifactEntity create(
            UUID jobId,
            String filePath,
            String content,
            GeneratedBy generatedBy,
            int generationRound) {
        String fileName = extractFileName(filePath);
        ArtifactType artifactType = ArtifactType.fromFilePath(filePath);
        Language language = Language.fromFileName(fileName);

        return G3ArtifactEntity.builder()
                .id(UUID.randomUUID()) // 显式生成UUID（MyBatis-Plus ASSIGN_UUID在PostgreSQL中不生效）
                .jobId(jobId)
                .artifactType(artifactType.getValue())
                .filePath(filePath)
                .fileName(fileName)
                .content(content)
                .language(language.getValue())
                .version(1)
                .hasErrors(false)
                .generatedBy(generatedBy.getValue())
                .generationRound(generationRound)
                .build();
    }

    /**
     * 从路径提取文件名
     */
    private static String extractFileName(String filePath) {
        if (filePath == null)
            return "unknown";
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }
}
