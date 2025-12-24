package com.ingenio.backend.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ingenio.backend.common.util.IngenioBusinessType;
import com.ingenio.backend.common.util.UUIDv8Generator;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * MyBatis-Plus自动填充处理器
 *
 * 功能：
 * - 自动填充id字段（INSERT时，使用UUIDv8Generator）
 * - 自动填充tenantId字段（INSERT时）
 * - 自动填充created_at字段（INSERT时）
 * - 自动填充updated_at字段（INSERT和UPDATE时）
 * - 自动填充deleted字段（INSERT时，默认值0）
 *
 * 配合Entity中的@TableField(fill = FieldFill.INSERT)注解使用
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MyBatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入操作自动填充
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");

        // 自动生成ID（如果为空）
        if (metaObject.hasGetter("id") && metaObject.getValue("id") == null) {
            String className = metaObject.getOriginalObject().getClass().getSimpleName().replace("Entity", "").toUpperCase();
            IngenioBusinessType businessType = getBusinessType(className);
            UUID generatedId = UUIDv8Generator.generate(businessType, 0); // 租户ID=0表示系统生成
            this.strictInsertFill(metaObject, "id", UUID.class, generatedId);
        }

        // 自动填充租户ID（如果为空），从TenantContextHolder获取
        if (metaObject.hasGetter("tenantId") && metaObject.getValue("tenantId") == null) {
            String tenantId = com.ingenio.backend.common.context.TenantContextHolder.getTenantId();
            if (org.springframework.util.StringUtils.hasText(tenantId)) {
                this.strictInsertFill(metaObject, "tenantId", String.class, tenantId);
            }
        }

        // 自动填充created_at
        this.strictInsertFill(metaObject, "createdAt", Instant.class, Instant.now());

        // 自动填充updated_at
        this.strictInsertFill(metaObject, "updatedAt", Instant.class, Instant.now());

        // 自动填充deleted（逻辑删除字段）
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);

        log.debug("插入填充完成");
    }

    /**
     * 根据类名获取业务类型
     */
    private IngenioBusinessType getBusinessType(String className) {
        return switch (className) {
            case "APPSPEC" -> IngenioBusinessType.APP_SPEC;
            case "APPSPECVERSION" -> IngenioBusinessType.APP_SPEC_VERSION;
            case "USER" -> IngenioBusinessType.USER;
            case "PROJECT" -> IngenioBusinessType.PROJECT;
            case "GENERATEDCODE" -> IngenioBusinessType.GENERATED_CODE;
            case "FORK" -> IngenioBusinessType.FORK;
            case "SOCIALINTERACTION" -> IngenioBusinessType.SOCIAL_INTERACTION;
            case "MAGICPROMPT" -> IngenioBusinessType.MAGIC_PROMPT;
            case "TENANT" -> IngenioBusinessType.TENANT;
            default -> IngenioBusinessType.OPERATION_LOG; // 默认类型
        };
    }

    /**
     * 更新操作自动填充
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");

        // 自动填充updated_at
        this.strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());

        log.debug("更新填充完成");
    }
}
