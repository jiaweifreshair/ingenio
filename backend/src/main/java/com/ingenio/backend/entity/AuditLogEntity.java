package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 审计日志实体类
 *
 * 用途：记录系统中的关键操作，支持审计追溯和合规要求
 *
 * 记录内容：
 * 1. 用户操作（登录/登出/权限变更）
 * 2. 资源变更（模板/Prompt/配置的CRUD）
 * 3. 管理操作（账号冻结/解冻/密钥轮换）
 *
 * 查询场景：
 * - JeecgBoot 查询 Ingenio 的操作日志
 * - 安全审计和合规检查
 * - 问题排查和追溯
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLogEntity {

    /**
     * 审计日志ID（UUID自动生成）
     *
     * 使用 IdType.AUTO 让 PostgreSQL 的 DEFAULT gen_random_uuid() 生成 UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID（多租户隔离）
     *
     * 用于：
     * - 按租户筛选日志
     * - 确保租户数据隔离
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 追踪ID（链路追踪）
     *
     * 用于：
     * - 关联同一请求的多条日志
     * - 分布式链路追踪
     */
    @TableField("trace_id")
    private String traceId;

    /**
     * 操作人ID
     *
     * 来源：
     * - C端用户：用户UUID
     * - 服务间调用：服务名称
     * - 系统任务：SYSTEM
     */
    @TableField("actor_id")
    private String actorId;

    /**
     * 操作人类型
     *
     * 枚举值：
     * - USER: C端用户
     * - ADMIN: 后台管理员
     * - SERVICE: 服务间调用
     * - SYSTEM: 系统自动任务
     */
    @TableField("actor_type")
    private String actorType;

    /**
     * 操作动作
     *
     * 命名规范：RESOURCE_ACTION
     *
     * 示例：
     * - USER_LOGIN: 用户登录
     * - USER_LOGOUT: 用户登出
     * - TEMPLATE_CREATE: 创建模板
     * - TEMPLATE_UPDATE: 更新模板
     * - TEMPLATE_DELETE: 删除模板
     * - ACCOUNT_FREEZE: 冻结账号
     * - KEY_ROTATE: 密钥轮换
     */
    @TableField("action")
    private String action;

    /**
     * 资源类型
     *
     * 示例：
     * - USER: 用户
     * - TEMPLATE: 模板
     * - PROMPT: Prompt
     * - CONFIG: 配置
     * - APP_SPEC: 应用规格
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 资源ID
     *
     * 被操作资源的唯一标识
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 操作前状态（JSON）
     *
     * 用于：
     * - 记录变更前的状态
     * - 支持回滚和对比
     *
     * 注意：敏感字段应脱敏后存储
     */
    @TableField(value = "before_state", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> beforeState;

    /**
     * 操作后状态（JSON）
     *
     * 用于：
     * - 记录变更后的状态
     * - 审计对比
     */
    @TableField(value = "after_state", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> afterState;

    /**
     * 操作结果
     *
     * 枚举值：
     * - SUCCESS: 成功
     * - FAILURE: 失败
     * - PARTIAL: 部分成功
     */
    @TableField("result")
    private String result;

    /**
     * 错误消息（失败时）
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 客户端IP地址
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * User-Agent
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 请求路径
     */
    @TableField("request_path")
    private String requestPath;

    /**
     * 请求方法（GET/POST/PUT/DELETE）
     */
    @TableField("request_method")
    private String requestMethod;

    /**
     * 扩展信息（JSON）
     *
     * 用于存储特定场景的额外信息
     */
    @TableField(value = "extra_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraData;

    /**
     * 创建时间（操作发生时间）
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
