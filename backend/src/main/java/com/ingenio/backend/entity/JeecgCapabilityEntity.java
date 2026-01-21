package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JeecgBoot能力清单实体类
 */
@TableName(value = "jeecg_capabilities", autoResultMap = true)
public class JeecgCapabilityEntity {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("icon")
    private String icon;

    @TableField("endpoint_prefix")
    private String endpointPrefix;

    @TableField(value = "apis", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> apis;

    @TableField(value = "config_template", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configTemplate;

    @TableField(value = "code_templates", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> codeTemplates;

    @TableField(value = "dependencies", typeHandler = JacksonTypeHandler.class)
    private List<String> dependencies;

    @TableField(value = "conflicts", typeHandler = JacksonTypeHandler.class)
    private List<String> conflicts;

    @TableField("version")
    private String version;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("doc_url")
    private String docUrl;

    @TableField(value = "examples", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> examples;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    // Constructors
    public JeecgCapabilityEntity() {
    }

    public JeecgCapabilityEntity(UUID id, String code, String name, String description, String category, String icon,
            String endpointPrefix, List<Map<String, Object>> apis, Map<String, Object> configTemplate,
            Map<String, Object> codeTemplates, List<String> dependencies, List<String> conflicts, String version,
            Boolean isActive, Integer sortOrder, String docUrl, Map<String, String> examples, Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.endpointPrefix = endpointPrefix;
        this.apis = apis;
        this.configTemplate = configTemplate;
        this.codeTemplates = codeTemplates;
        this.dependencies = dependencies;
        this.conflicts = conflicts;
        this.version = version;
        this.isActive = isActive;
        this.sortOrder = sortOrder;
        this.docUrl = docUrl;
        this.examples = examples;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static JeecgCapabilityEntityBuilder builder() {
        return new JeecgCapabilityEntityBuilder();
    }

    public static class JeecgCapabilityEntityBuilder {
        private UUID id;
        private String code;
        private String name;
        private String description;
        private String category;
        private String icon;
        private String endpointPrefix;
        private List<Map<String, Object>> apis;
        private Map<String, Object> configTemplate;
        private Map<String, Object> codeTemplates;
        private List<String> dependencies;
        private List<String> conflicts;
        private String version;
        private Boolean isActive;
        private Integer sortOrder;
        private String docUrl;
        private Map<String, String> examples;
        private Instant createdAt;
        private Instant updatedAt;

        JeecgCapabilityEntityBuilder() {
        }

        public JeecgCapabilityEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public JeecgCapabilityEntityBuilder code(String code) {
            this.code = code;
            return this;
        }

        public JeecgCapabilityEntityBuilder name(String name) {
            this.name = name;
            return this;
        }

        public JeecgCapabilityEntityBuilder description(String description) {
            this.description = description;
            return this;
        }

        public JeecgCapabilityEntityBuilder category(String category) {
            this.category = category;
            return this;
        }

        public JeecgCapabilityEntityBuilder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public JeecgCapabilityEntityBuilder endpointPrefix(String endpointPrefix) {
            this.endpointPrefix = endpointPrefix;
            return this;
        }

        public JeecgCapabilityEntityBuilder apis(List<Map<String, Object>> apis) {
            this.apis = apis;
            return this;
        }

        public JeecgCapabilityEntityBuilder configTemplate(Map<String, Object> configTemplate) {
            this.configTemplate = configTemplate;
            return this;
        }

        public JeecgCapabilityEntityBuilder codeTemplates(Map<String, Object> codeTemplates) {
            this.codeTemplates = codeTemplates;
            return this;
        }

        public JeecgCapabilityEntityBuilder dependencies(List<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        public JeecgCapabilityEntityBuilder conflicts(List<String> conflicts) {
            this.conflicts = conflicts;
            return this;
        }

        public JeecgCapabilityEntityBuilder version(String version) {
            this.version = version;
            return this;
        }

        public JeecgCapabilityEntityBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public JeecgCapabilityEntityBuilder sortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public JeecgCapabilityEntityBuilder docUrl(String docUrl) {
            this.docUrl = docUrl;
            return this;
        }

        public JeecgCapabilityEntityBuilder examples(Map<String, String> examples) {
            this.examples = examples;
            return this;
        }

        public JeecgCapabilityEntityBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public JeecgCapabilityEntityBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public JeecgCapabilityEntity build() {
            return new JeecgCapabilityEntity(id, code, name, description, category, icon, endpointPrefix, apis,
                    configTemplate, codeTemplates, dependencies, conflicts, version, isActive, sortOrder, docUrl,
                    examples, createdAt, updatedAt);
        }
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public String getEndpointPrefix() {
        return endpointPrefix;
    }

    public List<Map<String, Object>> getApis() {
        return apis;
    }

    public Map<String, Object> getConfigTemplate() {
        return configTemplate;
    }

    public Map<String, Object> getCodeTemplates() {
        return codeTemplates;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public String getVersion() {
        return version;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public String getDocUrl() {
        return docUrl;
    }

    public Map<String, String> getExamples() {
        return examples;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setId(UUID id) {
        this.id = id;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setEndpointPrefix(String endpointPrefix) {
        this.endpointPrefix = endpointPrefix;
    }

    public void setApis(List<Map<String, Object>> apis) {
        this.apis = apis;
    }

    public void setConfigTemplate(Map<String, Object> configTemplate) {
        this.configTemplate = configTemplate;
    }

    public void setCodeTemplates(Map<String, Object> codeTemplates) {
        this.codeTemplates = codeTemplates;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public void setConflicts(List<String> conflicts) {
        this.conflicts = conflicts;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setDocUrl(String docUrl) {
        this.docUrl = docUrl;
    }

    public void setExamples(Map<String, String> examples) {
        this.examples = examples;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
