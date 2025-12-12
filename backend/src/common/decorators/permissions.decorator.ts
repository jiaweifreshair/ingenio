/**
 * 权限装饰器
 * 用于标注路由处理器或控制器所需的权限和角色
 */
import { SetMetadata } from '@nestjs/common';
import { PERMISSIONS_KEY, ROLES_KEY } from '../guards/permission.guard';

/**
 * 权限装饰器
 * 标注访问路由所需的权限
 *
 * @param permissions - 权限列表
 * @returns 装饰器函数
 *
 * @example
 * ```typescript
 * @Permissions('appspec:read', 'appspec:write')
 * @Get()
 * getAppSpecs() { }
 * ```
 */
export const Permissions = (...permissions: string[]) => SetMetadata(PERMISSIONS_KEY, permissions);

/**
 * 角色装饰器
 * 标注访问路由所需的角色
 *
 * @param roles - 角色列表
 * @returns 装饰器函数
 *
 * @example
 * ```typescript
 * @Roles('admin', 'manager')
 * @Delete(':id')
 * deleteAppSpec() { }
 * ```
 */
export const Roles = (...roles: string[]) => SetMetadata(ROLES_KEY, roles);
