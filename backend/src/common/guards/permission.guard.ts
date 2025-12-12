/**
 * 权限守卫
 * 基于RBAC模型进行权限控制
 */
import { Injectable, CanActivate, ExecutionContext, ForbiddenException, Logger } from '@nestjs/common';
import { Reflector } from '@nestjs/core';

/**
 * 权限装饰器的metadata key
 */
export const PERMISSIONS_KEY = 'permissions';

/**
 * 角色装饰器的metadata key
 */
export const ROLES_KEY = 'roles';

/**
 * 权限守卫类
 * 验证用户是否具有访问资源所需的权限或角色
 */
@Injectable()
export class PermissionGuard implements CanActivate {
  private readonly logger = new Logger(PermissionGuard.name);

  constructor(private reflector: Reflector) {}

  /**
   * 判断是否允许访问
   *
   * @param context - 执行上下文
   * @returns 是否允许访问
   */
  canActivate(context: ExecutionContext): boolean {
    // 获取路由处理器和类上定义的所需权限
    const requiredPermissions = this.reflector.getAllAndOverride<string[]>(PERMISSIONS_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    // 获取路由处理器和类上定义的所需角色
    const requiredRoles = this.reflector.getAllAndOverride<string[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    // 如果没有定义权限和角色要求，则允许访问
    if (!requiredPermissions && !requiredRoles) {
      return true;
    }

    // 从请求对象中获取用户信息
    const request = context.switchToHttp().getRequest();
    const user = request.user;

    if (!user) {
      this.logger.warn('权限验证失败: 用户未认证');
      throw new ForbiddenException('需要认证才能访问此资源');
    }

    // 验证角色
    if (requiredRoles && requiredRoles.length > 0) {
      const hasRole = requiredRoles.includes(user.role);
      if (!hasRole) {
        this.logger.warn(
          `权限验证失败: 用户角色不匹配 - userId=${user.userId}, ` +
            `userRole=${user.role}, requiredRoles=${requiredRoles.join(',')}`,
        );
        throw new ForbiddenException('没有权限访问此资源');
      }
    }

    // 验证权限
    if (requiredPermissions && requiredPermissions.length > 0) {
      const hasPermission = requiredPermissions.some((permission) =>
        user.permissions.includes(permission),
      );

      if (!hasPermission) {
        this.logger.warn(
          `权限验证失败: 用户权限不足 - userId=${user.userId}, ` +
            `userPermissions=${user.permissions.join(',')}, requiredPermissions=${requiredPermissions.join(',')}`,
        );
        throw new ForbiddenException('没有权限访问此资源');
      }
    }

    this.logger.debug(
      `权限验证通过: userId=${user.userId}, role=${user.role}, path=${request.path}`,
    );

    return true;
  }
}
