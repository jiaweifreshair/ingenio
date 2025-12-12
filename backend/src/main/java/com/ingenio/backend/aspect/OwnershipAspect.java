package com.ingenio.backend.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.annotation.RequireOwnership;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.common.interfaces.OwnershipAware;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 资源所有权验证切面
 * 实现Row Level Security（行级安全）的AOP验证
 *
 * 功能：
 * - 在方法执行前验证当前用户是否为资源所有者
 * - 支持管理员跳过所有权检查
 * - 支持公开资源的访问控制
 *
 * 工作流程：
 * 1. 从@RequireOwnership注解获取配置
 * 2. 从方法参数中提取资源ID
 * 3. 根据资源类型查询资源
 * 4. 验证资源所有权
 * 5. 验证失败时抛出ForbiddenException
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final ProjectMapper projectMapper;
    private final AppSpecMapper appSpecMapper;
    private final com.ingenio.backend.mapper.GenerationTaskMapper generationTaskMapper;
    private final com.ingenio.backend.mapper.ApiKeyMapper apiKeyMapper;

    /**
     * 管理员角色列表
     * 这些角色可以跳过所有权检查（当allowAdmin=true时）
     */
    private static final List<String> ADMIN_ROLES = Arrays.asList(
            "ADMIN", "admin", "SUPER_ADMIN", "super_admin"
    );

    /**
     * 所有权验证前置通知
     * 在标注@RequireOwnership的方法执行前进行验证
     *
     * @param joinPoint 切入点
     * @param requireOwnership 注解实例
     * @throws ForbiddenException 当用户无权访问资源时抛出
     */
    @Before("@annotation(requireOwnership)")
    public void checkOwnership(JoinPoint joinPoint, RequireOwnership requireOwnership) {
        log.debug("开始所有权验证: resourceType={}, idParam={}",
                requireOwnership.resourceType(), requireOwnership.idParam());

        // 1. 检查用户是否已登录
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先登录");
        }

        // 2. 如果允许管理员跳过检查，检查当前用户角色
        if (requireOwnership.allowAdmin() && isCurrentUserAdmin()) {
            log.debug("管理员用户跳过所有权检查");
            return;
        }

        // 3. 获取资源ID
        UUID resourceId = extractResourceId(joinPoint, requireOwnership.idParam());
        if (resourceId == null) {
            log.warn("无法从参数中提取资源ID: idParam={}", requireOwnership.idParam());
            return; // 无法提取ID时不进行验证
        }

        // 4. 根据资源类型查询资源并验证所有权
        String resourceType = requireOwnership.resourceType();
        OwnershipAware resource = findResource(resourceType, resourceId);

        if (resource == null) {
            log.warn("资源不存在: type={}, id={}", resourceType, resourceId);
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        }

        // 5. 验证所有权
        UUID currentUserId = getCurrentUserId();
        if (!resource.isOwnedBy(currentUserId)) {
            log.warn("所有权验证失败: userId={}, resourceOwnerId={}, resourceType={}, resourceId={}",
                    currentUserId, resource.getOwnerId(), resourceType, resourceId);
            throw new BusinessException(ErrorCode.FORBIDDEN, requireOwnership.message());
        }

        log.debug("所有权验证通过: userId={}, resourceType={}, resourceId={}",
                currentUserId, resourceType, resourceId);
    }

    /**
     * 从方法参数中提取资源ID
     *
     * @param joinPoint 切入点
     * @param idParamName 参数名称
     * @return 资源ID，如果未找到返回null
     */
    private UUID extractResourceId(JoinPoint joinPoint, String idParamName) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = joinPoint.getArgs();

            // 遍历参数查找匹配的参数名
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getName().equals(idParamName)) {
                    Object arg = args[i];
                    if (arg instanceof UUID) {
                        return (UUID) arg;
                    } else if (arg instanceof String) {
                        return UUID.fromString((String) arg);
                    }
                }
            }

            // 尝试通过参数类型匹配（第一个UUID类型参数）
            for (Object arg : args) {
                if (arg instanceof UUID) {
                    return (UUID) arg;
                }
            }

        } catch (Exception e) {
            log.error("提取资源ID失败: idParamName={}", idParamName, e);
        }

        return null;
    }

    /**
     * 根据资源类型查询资源
     *
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @return 资源实例（实现OwnershipAware接口），如果未找到返回null
     */
    private OwnershipAware findResource(String resourceType, UUID resourceId) {
        switch (resourceType.toLowerCase()) {
            case "project":
                ProjectEntity project = projectMapper.selectById(resourceId);
                if (project != null) {
                    return new OwnershipAware() {
                        @Override
                        public UUID getOwnerId() {
                            return project.getUserId();
                        }

                        @Override
                        public UUID getTenantId() {
                            return project.getTenantId();
                        }
                    };
                }
                break;

            case "appspec":
                AppSpecEntity appSpec = appSpecMapper.selectById(resourceId);
                if (appSpec != null) {
                    return new OwnershipAware() {
                        @Override
                        public UUID getOwnerId() {
                            return appSpec.getCreatedByUserId();
                        }

                        @Override
                        public UUID getTenantId() {
                            return appSpec.getTenantId();
                        }
                    };
                }
                break;

            case "generation_task":
                com.ingenio.backend.entity.GenerationTaskEntity generationTask = generationTaskMapper.selectById(resourceId);
                if (generationTask != null) {
                    return new OwnershipAware() {
                        @Override
                        public UUID getOwnerId() {
                            return generationTask.getUserId();
                        }

                        @Override
                        public UUID getTenantId() {
                            return generationTask.getTenantId();
                        }
                    };
                }
                break;

            case "api_key":
                com.ingenio.backend.entity.ApiKeyEntity apiKey = apiKeyMapper.selectById(resourceId);
                if (apiKey != null) {
                    return new OwnershipAware() {
                        @Override
                        public UUID getOwnerId() {
                            return apiKey.getUserId();
                        }

                        @Override
                        public UUID getTenantId() {
                            // ApiKey没有tenantId字段，返回所有者的tenantId（需要从User表查询）
                            // 暂时返回null，后续可以优化
                            return null;
                        }
                    };
                }
                break;

            default:
                log.warn("不支持的资源类型: {}", resourceType);
                break;
        }

        return null;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 当前用户ID
     */
    private UUID getCurrentUserId() {
        Object loginId = StpUtil.getLoginId();
        return UUID.fromString(loginId.toString());
    }

    /**
     * 检查当前用户是否为管理员
     *
     * @return true如果是管理员
     */
    private boolean isCurrentUserAdmin() {
        try {
            List<String> roles = StpUtil.getRoleList();
            return roles.stream().anyMatch(ADMIN_ROLES::contains);
        } catch (Exception e) {
            log.error("检查管理员角色失败", e);
            return false;
        }
    }
}
