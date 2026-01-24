package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.common.utils.IdGenerator;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AppSpec规范实体类
 * 存储AI生成的应用规范（AppSpec JSON）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "app_specs", autoResultMap = true)
public class AppSpecEntity {

    /**
     * AppSpec ID（UUIDv8业务ID）
     */
    @TableId(value = "id", type = IdType.INPUT)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    /**
     * 租户ID - 用于租户隔离
     */
    @TableField(value = "tenant_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID tenantId;

    /**
     * 创建者用户ID
     */
    @TableField(value = "created_by_user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID createdByUserId;

    /**
     * AppSpec完整JSON内容（包含pages/dataModels/flows/permissions）
     */
    @TableField(value = "spec_content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> specContent;

    /**
     * 版本号（从1开始递增）
     */
    @TableField("version")
    private Integer version;

    /**
     * 父版本ID（用于版本链追踪）
     */
    @TableField(value = "parent_version_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID parentVersionId;

    /**
     * 状态：draft/validated/generated/published
     */
    @TableField("status")
    private String status;

    /**
     * 质量评分（0-100）- 由ValidateAgent生成
     */
    @TableField("quality_score")
    private Integer qualityScore;

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

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    // ==================== V2.0 新增字段（意图识别相关）====================

    /**
     * V2.0意图类型（新增）
     * 用户需求的意图分类：
     * - CLONE_EXISTING_WEBSITE: 克隆已有网站
     * - DESIGN_FROM_SCRATCH: 从零开始设计
     * - HYBRID_CLONE_AND_CUSTOMIZE: 混合模式（克隆+定制）
     */
    @TableField(value = "intent_type", exist = false)
    private String intentType;

    /**
     * V2.0置信度评分（新增）
     * 意图识别的置信度分数，范围：0.00 - 1.00
     * 使用BigDecimal匹配数据库DECIMAL(3,2)类型，确保精度
     */
    @TableField(value = "confidence_score", exist = false)
    private java.math.BigDecimal confidenceScore;

    /**
     * V2.0匹配的模板列表（新增）
     * 根据用户需求匹配的行业模板ID列表（JSONB格式）
     * 例如：["template-airbnb-001", "template-booking-002"]
     */
    @TableField(value = "matched_templates", typeHandler = JacksonTypeHandler.class, exist = false)
    private java.util.List<String> matchedTemplates;

    /**
     * V2.0选中的模板ID（新增）
     * 用户最终选择的行业模板ID（如果选择了模板）
     */
    @TableField(value = "selected_template_id", typeHandler = UUIDv8TypeHandler.class, exist = false)
    private UUID selectedTemplateId;

    /**
     * V2.0选中的设计风格（新增）
     * SuperDesign 7种风格之一：
     * - A: 现代极简
     * - B: 活力时尚
     * - C: 经典专业
     * - D: 未来科技
     * - E: 沉浸式3D
     * - F: 游戏化设计
     * - G: 自然流动
     */
    @TableField(value = "selected_style", exist = false)
    private String selectedStyle;

    /**
     * V2.0设计确认标志（新增）
     * 用户是否已确认设计方案（确认后才进入Execute阶段）
     * 默认值：false（未确认）
     */
    @TableField(value = "design_confirmed", exist = false)
    private Boolean designConfirmed;

    /**
     * V2.0设计确认时间（新增）
     * 用户点击"确认设计"的时间戳
     */
    @TableField(value = "design_confirmed_at", exist = false)
    private Instant designConfirmedAt;

    // ==================== V2.0 新增字段（原型预览相关）====================

    /**
     * V2.0前端原型代码（新增）
     * OpenLovable生成的React原型代码（JSONB格式）
     * 包含：组件代码、路由配置、样式文件等
     */
    @TableField(value = "frontend_prototype", typeHandler = JacksonTypeHandler.class, exist = false)
    private Map<String, Object> frontendPrototype;

    /**
     * V2.0前端原型预览URL（新增）
     * E2B Sandbox中部署的原型预览地址
     * 例如：https://sandbox-xxx.e2b.dev
     */
    @TableField(value = "frontend_prototype_url", exist = false)
    private String frontendPrototypeUrl;

    /**
     * V2.0原型生成时间（新增）
     * OpenLovable开始生成原型的时间戳
     */
    @TableField(value = "prototype_generated_at", exist = false)
    private Instant prototypeGeneratedAt;

    /**
     * V2.0意图识别结果（新增）
     * IntentClassifier返回的完整分类结果（JSONB格式）
     * 包含：intent、confidence、reasoning、keywords、urls等
     */
    @TableField(value = "intent_classification_result", typeHandler = JacksonTypeHandler.class, exist = false)
    private Map<String, Object> intentClassificationResult;

    // ==================== Blueprint（蓝图规范）====================

    /**
     * Blueprint ID（可选）
     * 来自 blueprintSpec.id，用于快速追溯当前AppSpec绑定的蓝图
     */
    @TableField(value = "blueprint_id", typeHandler = UUIDv8TypeHandler.class, exist = false)
    private UUID blueprintId;

    /**
     * Blueprint 完整规范（JSONB）
     * 用于在运行期注入约束（Blueprint Mode）并做合规性校验
     */
    @TableField(value = "blueprint_spec", typeHandler = JacksonTypeHandler.class, exist = false)
    private Map<String, Object> blueprintSpec;

    /**
     * Blueprint 模式是否启用
     * true 表示生成流程必须遵守 blueprintSpec 中的约束
     */
    @TableField(value = "blueprint_mode_enabled", exist = false)
    private Boolean blueprintModeEnabled;

    // ==================== AppSpec状态枚举 ====================

    /**
     * AppSpec状态枚举
     */
    public enum Status {
        DRAFT("draft"),
        VALIDATED("validated"),
        GENERATED("generated"),
        PUBLISHED("published");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
