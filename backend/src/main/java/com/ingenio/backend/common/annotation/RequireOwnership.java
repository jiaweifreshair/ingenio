package com.ingenio.backend.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 资源所有权验证注解
 * 用于标注需要验证资源所有权的方法
 *
 * 使用场景：
 * - Service层方法：验证用户是否为资源所有者
 * - Controller层方法：自动检查请求参数中的资源ID
 *
 * 使用示例：
 * <pre>{@code
 * @Service
 * public class ProjectService {
 *
 *     @RequireOwnership(resourceType = "project", idParam = "projectId")
 *     public void updateProject(UUID projectId, UpdateProjectDTO dto) {
 *         // 方法执行前会自动验证当前用户是否为项目所有者
 *         // 如果不是所有者，抛出ForbiddenException
 *     }
 *
 *     @RequireOwnership(resourceType = "appspec", idParam = "appSpecId", allowAdmin = true)
 *     public AppSpecEntity getAppSpec(UUID appSpecId) {
 *         // allowAdmin=true 时，管理员可以访问任何资源
 *     }
 * }
 * }</pre>
 *
 * 验证逻辑：
 * 1. 从方法参数中提取资源ID
 * 2. 查询数据库获取资源
 * 3. 验证资源实现OwnershipAware接口
 * 4. 比较资源所有者ID与当前登录用户ID
 * 5. 如果不匹配且用户不是管理员，抛出ForbiddenException
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see com.ingenio.backend.common.interfaces.OwnershipAware
 * @see com.ingenio.backend.aspect.OwnershipAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireOwnership {

    /**
     * 资源类型
     * 用于识别要验证的资源类型
     *
     * 支持的值：
     * - "project": 项目资源
     * - "appspec": 应用规范资源
     * - "generation_task": 生成任务资源
     * - "api_key": API密钥资源
     *
     * @return 资源类型字符串
     */
    String resourceType();

    /**
     * 资源ID参数名称
     * 指定方法参数中用于标识资源ID的参数名
     *
     * 默认值："id"
     *
     * @return 参数名称
     */
    String idParam() default "id";

    /**
     * 是否允许管理员跳过所有权检查
     * 如果为true，ADMIN和SUPER_ADMIN角色可以访问任何资源
     *
     * 默认值：true
     *
     * @return true表示允许管理员访问
     */
    boolean allowAdmin() default true;

    /**
     * 是否允许访问公开资源
     * 如果为true，visibility为PUBLIC的资源可以被任何人访问（仅读取）
     *
     * 默认值：false
     *
     * @return true表示允许访问公开资源
     */
    boolean allowPublic() default false;

    /**
     * 验证失败时的错误消息
     *
     * @return 错误消息
     */
    String message() default "无权访问此资源";
}
