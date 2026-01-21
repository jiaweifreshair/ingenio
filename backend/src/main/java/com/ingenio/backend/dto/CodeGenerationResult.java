package com.ingenio.backend.dto;

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

    public CodeGenerationResult() {
    }

    public CodeGenerationResult(UUID taskId, UUID tenantId, String projectRoot, List<GeneratedFile> files,
            String projectType, String techStack, Map<String, String> buildConfigs, List<Dependency> dependencies,
            Map<String, String> environmentVariables, String databaseSchema, SupabaseConfig supabaseConfig) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.projectRoot = projectRoot;
        this.files = files;
        this.projectType = projectType;
        this.techStack = techStack;
        this.buildConfigs = buildConfigs;
        this.dependencies = dependencies;
        this.environmentVariables = environmentVariables;
        this.databaseSchema = databaseSchema;
        this.supabaseConfig = supabaseConfig;
    }

    public static CodeGenerationResultBuilder builder() {
        return new CodeGenerationResultBuilder();
    }

    public static class CodeGenerationResultBuilder {
        private UUID taskId;
        private UUID tenantId;
        private String projectRoot;
        private List<GeneratedFile> files;
        private String projectType;
        private String techStack;
        private Map<String, String> buildConfigs;
        private List<Dependency> dependencies;
        private Map<String, String> environmentVariables;
        private String databaseSchema;
        private SupabaseConfig supabaseConfig;

        public CodeGenerationResultBuilder taskId(UUID taskId) {
            this.taskId = taskId;
            return this;
        }

        public CodeGenerationResultBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public CodeGenerationResultBuilder projectRoot(String projectRoot) {
            this.projectRoot = projectRoot;
            return this;
        }

        public CodeGenerationResultBuilder files(List<GeneratedFile> files) {
            this.files = files;
            return this;
        }

        public CodeGenerationResultBuilder projectType(String projectType) {
            this.projectType = projectType;
            return this;
        }

        public CodeGenerationResultBuilder techStack(String techStack) {
            this.techStack = techStack;
            return this;
        }

        public CodeGenerationResultBuilder buildConfigs(Map<String, String> buildConfigs) {
            this.buildConfigs = buildConfigs;
            return this;
        }

        public CodeGenerationResultBuilder dependencies(List<Dependency> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public CodeGenerationResultBuilder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public CodeGenerationResultBuilder databaseSchema(String databaseSchema) {
            this.databaseSchema = databaseSchema;
            return this;
        }

        public CodeGenerationResultBuilder supabaseConfig(SupabaseConfig supabaseConfig) {
            this.supabaseConfig = supabaseConfig;
            return this;
        }

        public CodeGenerationResult build() {
            return new CodeGenerationResult(taskId, tenantId, projectRoot, files, projectType, techStack, buildConfigs,
                    dependencies, environmentVariables, databaseSchema, supabaseConfig);
        }
    }

    // Getters and Setters
    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public List<GeneratedFile> getFiles() {
        return files;
    }

    public void setFiles(List<GeneratedFile> files) {
        this.files = files;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getTechStack() {
        return techStack;
    }

    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }

    public Map<String, String> getBuildConfigs() {
        return buildConfigs;
    }

    public void setBuildConfigs(Map<String, String> buildConfigs) {
        this.buildConfigs = buildConfigs;
    }

    public List<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public String getDatabaseSchema() {
        return databaseSchema;
    }

    public void setDatabaseSchema(String databaseSchema) {
        this.databaseSchema = databaseSchema;
    }

    public SupabaseConfig getSupabaseConfig() {
        return supabaseConfig;
    }

    public void setSupabaseConfig(SupabaseConfig supabaseConfig) {
        this.supabaseConfig = supabaseConfig;
    }

    /**
     * 生成的文件
     */
    public static class GeneratedFile {
        private String path;
        private String content;
        private String type;
        private Long size;

        public GeneratedFile() {
        }

        public GeneratedFile(String path, String content, String type, Long size) {
            this.path = path;
            this.content = content;
            this.type = type;
            this.size = size;
        }

        public static GeneratedFileBuilder builder() {
            return new GeneratedFileBuilder();
        }

        public static class GeneratedFileBuilder {
            private String path;
            private String content;
            private String type;
            private Long size;

            public GeneratedFileBuilder path(String path) {
                this.path = path;
                return this;
            }

            public GeneratedFileBuilder content(String content) {
                this.content = content;
                return this;
            }

            public GeneratedFileBuilder type(String type) {
                this.type = type;
                return this;
            }

            public GeneratedFileBuilder size(Long size) {
                this.size = size;
                return this;
            }

            public GeneratedFile build() {
                return new GeneratedFile(path, content, type, size);
            }
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }
    }

    /**
     * 依赖信息
     */
    public static class Dependency {
        private String coordinate;
        private String version;
        private String scope;

        public Dependency() {
        }

        public Dependency(String coordinate, String version, String scope) {
            this.coordinate = coordinate;
            this.version = version;
            this.scope = scope;
        }

        public static DependencyBuilder builder() {
            return new DependencyBuilder();
        }

        public static class DependencyBuilder {
            private String coordinate;
            private String version;
            private String scope;

            public DependencyBuilder coordinate(String coordinate) {
                this.coordinate = coordinate;
                return this;
            }

            public DependencyBuilder version(String version) {
                this.version = version;
                return this;
            }

            public DependencyBuilder scope(String scope) {
                this.scope = scope;
                return this;
            }

            public Dependency build() {
                return new Dependency(coordinate, version, scope);
            }
        }

        public String getCoordinate() {
            return coordinate;
        }

        public void setCoordinate(String coordinate) {
            this.coordinate = coordinate;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }

    /**
     * Supabase配置
     */
    public static class SupabaseConfig {
        private String url;
        private String apiKey;
        private Boolean anonymous;

        public SupabaseConfig() {
        }

        public SupabaseConfig(String url, String apiKey, Boolean anonymous) {
            this.url = url;
            this.apiKey = apiKey;
            this.anonymous = anonymous;
        }

        public static SupabaseConfigBuilder builder() {
            return new SupabaseConfigBuilder();
        }

        public static class SupabaseConfigBuilder {
            private String url;
            private String apiKey;
            private Boolean anonymous;

            public SupabaseConfigBuilder url(String url) {
                this.url = url;
                return this;
            }

            public SupabaseConfigBuilder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public SupabaseConfigBuilder anonymous(Boolean anonymous) {
                this.anonymous = anonymous;
                return this;
            }

            public SupabaseConfig build() {
                return new SupabaseConfig(url, apiKey, anonymous);
            }
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Boolean getAnonymous() {
            return anonymous;
        }

        public void setAnonymous(Boolean anonymous) {
            this.anonymous = anonymous;
        }
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
