package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 行业应用模板实体类
 *
 * Phase X.4: 行业模板库功能
 * 用途：存储预定义的行业应用模板，支持基于关键词和分类的智能匹配
 *
 * 业务场景：
 * 1. 意图识别后推荐相关行业模板
 * 2. 用户浏览和选择行业模板
 * 3. 基于模板快速生成应用原型
 *
 * 关键字段：
 * - keywords: 用于模板匹配算法（Jaccard相似度计算）
 * - entities: 预定义实体列表（减少AI生成时间）
 * - features: 核心功能清单（提供业务参考）
 * - complexityScore: 复杂度评分（帮助用户选择合适模板）
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 行业模板库开发)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "industry_templates", autoResultMap = true)
public class IndustryTemplateEntity {

    /**
     * 模板ID（UUID自动生成）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    // ==================== 基础信息 ====================

    /**
     * 模板名称
     *
     * 示例：
     * - "民宿预订平台模板"
     * - "在线教育平台模板"
     * - "社交论坛平台模板"
     *
     * 命名规范：[行业] + [类型] + "模板"
     */
    @TableField("name")
    private String name;

    /**
     * 详细描述
     *
     * 包含内容：
     * 1. 适用场景说明
     * 2. 核心功能概述
     * 3. 目标用户群体
     * 4. 技术特点说明
     *
     * 长度：200-500字
     */
    @TableField("description")
    private String description;

    /**
     * 一级分类
     *
     * 枚举值：
     * - "电商类"
     * - "教育类"
     * - "社交类"
     * - "生活服务类"
     * - "企业管理类"
     * - "金融科技类"
     * - "内容媒体类"
     */
    @TableField("category")
    private String category;

    /**
     * 二级分类
     *
     * 示例（电商类）：
     * - "综合电商"
     * - "垂直电商"
     * - "跨境电商"
     * - "社交电商"
     */
    @TableField("subcategory")
    private String subcategory;

    // ==================== 匹配算法相关 ====================

    /**
     * 关键词数组（JSONB类型）
     *
     * 用于模板匹配算法：
     * - 计算与用户输入的Jaccard相似度
     * - 支持GIN索引快速查询
     *
     * 格式：JSON数组 ["关键词1", "关键词2", ...]
     *
     * 示例（民宿预订模板）：
     * ["民宿", "预订", "住宿", "airbnb", "短租", "房源", "在线预订"]
     *
     * 要求：
     * - 数量：5-10个关键词
     * - 权重：核心业务词 > 相似词 > 扩展词
     * - 多语言：支持中英文混合
     */
    @TableField(value = "keywords", typeHandler = JacksonTypeHandler.class)
    private List<String> keywords;

    // ==================== 参考来源 ====================

    /**
     * 参考网站URL
     *
     * 作用：
     * 1. 提供真实案例参考
     * 2. 用户可以浏览了解业务细节
     * 3. 爬虫生成时作为数据源
     *
     * 示例：
     * - "https://www.airbnb.com"
     * - "https://www.coursera.org"
     * - "https://www.zhihu.com"
     *
     * 要求：必须是公开可访问的网站
     */
    @TableField("reference_url")
    private String referenceUrl;

    /**
     * 模板预览图URL
     *
     * 用途：
     * 1. 前端模板选择页展示
     * 2. 提升用户体验和选择效率
     *
     * 规格：
     * - 分辨率：1200x800 或 16:9
     * - 格式：PNG/JPEG
     * - 大小：< 500KB
     */
    @TableField("thumbnail_url")
    private String thumbnailUrl;

    // ==================== 业务定义（核心字段）====================

    /**
     * 预定义实体列表（JSONB类型）
     *
     * 作用：
     * 1. 减少AI生成时的推理时间
     * 2. 确保实体定义的准确性
     * 3. 提供业务模型参考
     *
     * 格式：JSON数组，每个元素为一个实体对象
     * [
     *   {
     *     "name": "User",
     *     "description": "用户实体",
     *     "attributes": [
     *       {"name": "email", "type": "string", "required": true},
     *       {"name": "password", "type": "string", "required": true},
     *       {"name": "nickname", "type": "string"}
     *     ]
     *   },
     *   {
     *     "name": "Listing",
     *     "description": "房源实体",
     *     "attributes": [...]
     *   }
     * ]
     *
     * 要求：
     * - 数量：3-10个核心实体
     * - 完整性：包含实体名、描述、属性列表
     * - 准确性：属性类型必须明确（string/number/boolean/date）
     */
    @TableField(value = "entities", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> entities;

    /**
     * 核心功能清单（JSONB类型）
     *
     * 作用：
     * 1. 展示模板涵盖的主要功能
     * 2. 帮助用户快速了解业务范围
     * 3. 生成AppSpec时作为功能参考
     *
     * 格式：JSON数组，字符串列表
     * [
     *   "用户注册登录",
     *   "房源浏览搜索",
     *   "在线预订支付",
     *   "订单管理",
     *   "评价反馈系统",
     *   "房东房源管理",
     *   "消息通知"
     * ]
     *
     * 要求：
     * - 数量：5-15个功能
     * - 粒度：粗粒度功能（不是技术细节）
     * - 排序：按业务重要性排序
     */
    @TableField(value = "features", typeHandler = JacksonTypeHandler.class)
    private List<String> features;

    /**
     * 业务流程定义（JSONB类型）
     *
     * 作用：
     * 1. 定义关键业务流程
     * 2. 辅助生成workflow定义
     *
     * 格式：JSON数组，每个元素为一个流程对象
     * [
     *   {
     *     "name": "预订流程",
     *     "steps": ["浏览房源", "选择日期", "确认预订", "支付订金", "等待确认"],
     *     "description": "用户预订房源的完整流程"
     *   },
     *   {
     *     "name": "房东审核流程",
     *     "steps": [...],
     *     "description": "..."
     *   }
     * ]
     *
     * 可选字段，不是所有模板都需要
     */
    @TableField(value = "workflows", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> workflows;

    // ==================== 蓝图规范（Blueprint）====================

    /**
     * 模版蓝图规范定义（JSONB类型）
     *
     * 作用：
     * 1. 约束AI代码生成行为（Blueprint Mode）
     * 2. 定义核心表结构和API规范
     * 3. 提供生成约束指令
     *
     * 格式：JSON对象
     * {
     *   "meta": {
     *     "templateId": "campus-marketplace",
     *     "version": "1.0.0",
     *     "description": "校园二手交易平台"
     *   },
     *   "constraints": {
     *     "coreTables": ["users", "products", "orders"],
     *     "allowCustomFields": true,
     *     "disallowRename": true
     *   },
     *   "schema": {
     *     "users": {
     *       "id": "UUID PRIMARY KEY",
     *       "email": "VARCHAR(255) NOT NULL UNIQUE",
     *       "password_hash": "VARCHAR(255) NOT NULL"
     *     }
     *   },
     *   "apis": [
     *     {"method": "POST", "path": "/api/users", "description": "用户注册"}
     *   ],
     *   "features": ["user-auth", "product-crud", "order-management"]
     * }
     *
     * @since V023 Blueprint-Driven Architecture
     */
    @TableField(value = "blueprint_spec", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> blueprintSpec;

    // ==================== 技术栈建议 ====================

    /**
     * 技术栈建议（JSONB类型）
     *
     * 作用：
     * 1. 推荐适合该模板的技术选型
     * 2. 帮助用户了解技术复杂度
     *
     * 格式：JSON对象
     * {
     *   "frontend": ["React", "Next.js", "TailwindCSS"],
     *   "backend": ["Spring Boot", "PostgreSQL", "Redis"],
     *   "infrastructure": ["Docker", "Nginx"]
     * }
     *
     * 可选字段
     */
    @TableField(value = "tech_stack", typeHandler = JacksonTypeHandler.class)
    private Map<String, List<String>> techStack;

    // ==================== 复杂度和估算 ====================

    /**
     * 复杂度评分（1-10）
     *
     * 评分标准：
     * 1-3: 简单（单表CRUD，基础功能）
     * 4-6: 中等（多表关联，用户权限，支付集成）
     * 7-9: 复杂（实时通信，复杂业务逻辑，分布式架构）
     * 10: 极复杂（大规模分布式系统，AI集成，高并发处理）
     *
     * 用途：
     * - 帮助用户选择适合的模板
     * - 计算预估开发时间
     * - 在匹配算法中作为复杂度惩罚因子
     *
     * 默认值：5（中等复杂度）
     */
    @TableField("complexity_score")
    private Integer complexityScore;

    /**
     * 预估开发工时（小时）
     *
     * 计算依据：
     * - 基于历史数据统计
     * - 包含前后端开发、测试、部署
     * - 基于熟练开发者的平均时间
     *
     * 示例：
     * - 简单CRUD应用：20-40小时
     * - 中等复杂应用：60-120小时
     * - 复杂应用：150-300小时
     *
     * 可选字段
     */
    @TableField("estimated_hours")
    private Integer estimatedHours;

    // ==================== 使用统计 ====================

    /**
     * 使用次数统计
     *
     * 作用：
     * 1. 统计模板受欢迎程度
     * 2. 用于"热门模板"排序
     * 3. 用于推荐算法优化
     *
     * 更新时机：
     * - 用户选择该模板时 +1
     * - 由应用层代码更新（非数据库触发器）
     *
     * 默认值：0
     */
    @TableField("usage_count")
    private Integer usageCount;

    /**
     * 用户评分（0-5）
     *
     * 计算方式：
     * - 基于使用该模板的用户反馈
     * - 加权平均分（考虑评分时间和用户权重）
     *
     * 用途：
     * - 展示模板质量
     * - 用于"优质模板"排序
     * - 匹配算法中作为质量因子
     *
     * 可选字段（初始为NULL）
     */
    @TableField("rating")
    private BigDecimal rating;

    // ==================== 状态管理 ====================

    /**
     * 是否启用
     *
     * 作用：
     * - 软删除机制
     * - 版本控制（禁用旧版本，启用新版本）
     *
     * 默认值：true
     */
    @TableField("is_active")
    private Boolean isActive;

    // ==================== 审计字段 ====================

    /**
     * 创建人
     *
     * 记录模板的创建者或维护者
     * - 系统内置模板：SYSTEM
     * - 用户自定义模板：用户邮箱
     *
     * 可选字段
     */
    @TableField("created_by")
    private String createdBy;

    /**
     * 创建时间
     *
     * 自动填充策略：INSERT时自动生成当前时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     *
     * 自动填充策略：INSERT和UPDATE时自动更新当前时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
