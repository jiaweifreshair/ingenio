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
 * 项目能力配置实体类
 *
 * 用途：存储用户为项目配置的JeecgBoot能力参数，
 * 敏感信息（如API密钥）会进行加密存储。
 *
 * 业务场景：
 * 1. 用户在创建项目时选择需要的JeecgBoot能力
 * 2. 用户填写各能力的配置参数（如支付宝AppId、短信签名等）
 * 3. G3引擎生成代码时读取配置生成集成代码
 *
 * 安全设计：
 * - configValues中标记为encrypted的字段使用AES-256-GCM加密
 * - 加密密钥从环境变量读取，不存储在代码或数据库中
 * - API返回时敏感字段会被掩码处理
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎JeecgBoot能力集成)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "project_capability_configs", autoResultMap = true)
public class ProjectCapabilityConfigEntity {

    /**
     * 配置ID（UUID自动生成）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    // ==================== 关联信息 ====================

    /**
     * 所属项目ID
     *
     * 关联projects表，级联删除
     */
    @TableField("project_id")
    private UUID projectId;

    /**
     * 能力ID
     *
     * 关联jeecg_capabilities表
     */
    @TableField("capability_id")
    private UUID capabilityId;

    /**
     * 能力代码
     *
     * 冗余存储，方便查询
     * 如：auth, payment_alipay, sms_aliyun
     */
    @TableField("capability_code")
    private String capabilityCode;

    // ==================== 配置数据 ====================

    /**
     * 配置值（JSONB类型）
     *
     * 存储用户填写的配置参数，敏感字段已加密
     *
     * 格式示例（支付宝）：
     * {
     *   "appId": "2021001234567890",
     *   "privateKey": "ENC:AES256:base64encodedciphertext...",
     *   "alipayPublicKey": "ENC:AES256:base64encodedciphertext...",
     *   "notifyUrl": "https://api.example.com/payment/alipay/notify",
     *   "sandbox": true
     * }
     *
     * 加密字段格式：ENC:算法:密文
     */
    @TableField(value = "config_values", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configValues;

    // ==================== 状态管理 ====================

    /**
     * 配置状态
     *
     * 枚举值：
     * - pending: 待验证（刚配置，未验证连通性）
     * - validated: 已验证（验证通过，可以使用）
     * - failed: 验证失败（配置有误，需要修改）
     */
    @TableField("status")
    private String status;

    /**
     * 验证错误信息
     *
     * 当status为failed时，存储具体的错误信息
     * 帮助用户定位配置问题
     */
    @TableField("validation_error")
    private String validationError;

    /**
     * 最后验证时间
     *
     * 记录最近一次验证配置的时间
     */
    @TableField("validated_at")
    private Instant validatedAt;

    // ==================== 审计字段 ====================

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

    // ==================== 常量定义 ====================

    /** 状态：待验证 */
    public static final String STATUS_PENDING = "pending";
    /** 状态：已验证 */
    public static final String STATUS_VALIDATED = "validated";
    /** 状态：验证失败 */
    public static final String STATUS_FAILED = "failed";

    /** 加密字段前缀 */
    public static final String ENCRYPTED_PREFIX = "ENC:AES256:";
}
