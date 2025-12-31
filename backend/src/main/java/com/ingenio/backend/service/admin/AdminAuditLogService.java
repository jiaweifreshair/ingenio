package com.ingenio.backend.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.AuditLogEntity;
import com.ingenio.backend.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;

/**
 * Admin API - 审计日志服务
 *
 * 用途：
 * 1. 记录系统关键操作
 * 2. 供 JeecgBoot 查询审计日志
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 记录审计日志
     *
     * @param tenantId     租户ID
     * @param traceId      链路追踪ID
     * @param actorId      操作人ID
     * @param actorType    操作人类型
     * @param action       操作动作
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param beforeState  操作前状态
     * @param afterState   操作后状态
     * @param result       操作结果
     * @param errorMessage 错误消息
     * @param clientIp     客户端IP
     * @param userAgent    User-Agent
     * @param requestPath  请求路径
     * @param requestMethod 请求方法
     * @param extraData    扩展数据
     * @return 审计日志实体
     */
    public AuditLogEntity log(
            String tenantId,
            String traceId,
            String actorId,
            String actorType,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            String result,
            String errorMessage,
            String clientIp,
            String userAgent,
            String requestPath,
            String requestMethod,
            Map<String, Object> extraData
    ) {
        AuditLogEntity entity = AuditLogEntity.builder()
                .tenantId(tenantId)
                .traceId(traceId)
                .actorId(actorId)
                .actorType(actorType != null ? actorType : "USER")
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .beforeState(beforeState)
                .afterState(afterState)
                .result(result != null ? result : "SUCCESS")
                .errorMessage(errorMessage)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .requestPath(requestPath)
                .requestMethod(requestMethod)
                .extraData(extraData)
                .createdAt(Instant.now())
                .build();

        auditLogMapper.insert(entity);
        log.debug("审计日志已记录: action={}, resourceType={}, resourceId={}",
                action, resourceType, resourceId);
        return entity;
    }

    /**
     * 简化的日志记录方法
     *
     * @param actorId      操作人ID
     * @param action       操作动作
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param result       操作结果
     * @return 审计日志实体
     */
    public AuditLogEntity logSimple(
            String actorId,
            String action,
            String resourceType,
            String resourceId,
            String result
    ) {
        return log(null, null, actorId, "USER", action, resourceType, resourceId,
                null, null, result, null, null, null, null, null, null);
    }

    /**
     * 分页查询审计日志
     *
     * @param tenantId      租户ID（可选）
     * @param actorId       操作人ID（可选）
     * @param action        操作动作（可选）
     * @param resourceType  资源类型（可选）
     * @param startTime     开始时间（可选）
     * @param endTime       结束时间（可选）
     * @param pageNum       页码（从1开始）
     * @param pageSize      每页大小
     * @return 分页结果
     */
    public Page<AuditLogEntity> queryLogs(
            String tenantId,
            String actorId,
            String action,
            String resourceType,
            Instant startTime,
            Instant endTime,
            int pageNum,
            int pageSize
    ) {
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(AuditLogEntity::getTenantId, tenantId);
        }
        if (StringUtils.hasText(actorId)) {
            wrapper.eq(AuditLogEntity::getActorId, actorId);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(AuditLogEntity::getAction, action);
        }
        if (StringUtils.hasText(resourceType)) {
            wrapper.eq(AuditLogEntity::getResourceType, resourceType);
        }
        if (startTime != null) {
            wrapper.ge(AuditLogEntity::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuditLogEntity::getCreatedAt, endTime);
        }

        // 按时间倒序
        wrapper.orderByDesc(AuditLogEntity::getCreatedAt);

        Page<AuditLogEntity> page = new Page<>(pageNum, pageSize);
        return auditLogMapper.selectPage(page, wrapper);
    }

    /**
     * 根据链路ID查询日志
     *
     * @param traceId 链路追踪ID
     * @return 日志列表
     */
    public java.util.List<AuditLogEntity> queryByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return java.util.Collections.emptyList();
        }

        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLogEntity::getTraceId, traceId)
                .orderByAsc(AuditLogEntity::getCreatedAt);
        return auditLogMapper.selectList(wrapper);
    }
}
