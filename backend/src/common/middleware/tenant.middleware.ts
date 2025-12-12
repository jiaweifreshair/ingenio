/**
 * 租户隔离中间件
 * 确保每个请求都在正确的租户上下文中执行
 */
import { Injectable, NestMiddleware, UnauthorizedException, Logger } from '@nestjs/common';
import { Request, Response, NextFunction } from 'express';

/**
 * 用户信息接口
 */
export interface AuthenticatedUser {
  userId: string;
  username: string;
  tenantId: string;
  role: string;
  permissions: string[];
}

/**
 * 扩展Request接口，添加tenantId字段
 */
declare global {
  namespace Express {
    interface Request {
      /** 当前请求的租户ID */
      tenantId?: string;
    }
    interface User extends AuthenticatedUser {}
  }
}

/**
 * 租户隔离中间件类
 * 从JWT token或header中提取租户ID，确保数据隔离
 */
@Injectable()
export class TenantMiddleware implements NestMiddleware {
  private readonly logger = new Logger(TenantMiddleware.name);

  /**
   * 中间件处理函数
   *
   * @param req - HTTP请求对象
   * @param _res - HTTP响应对象（未使用）
   * @param next - 下一个中间件函数
   */
  use(req: Request, _res: Response, next: NextFunction) {
    // 优先从user对象获取tenantId（JWT认证后的请求）
    if (req.user && req.user.tenantId) {
      req.tenantId = req.user.tenantId;
      this.logger.debug(`租户上下文设置: tenantId=${req.tenantId}, path=${req.path}`);
      return next();
    }

    // 其次从header中获取tenantId（公共API、注册登录等）
    const tenantIdFromHeader = req.headers['x-tenant-id'] as string;
    if (tenantIdFromHeader) {
      req.tenantId = tenantIdFromHeader;
      this.logger.debug(`租户上下文设置（header）: tenantId=${req.tenantId}, path=${req.path}`);
      return next();
    }

    // 对于某些公共路径，允许不提供tenantId
    const publicPaths = ['/api/auth/login', '/api/auth/register', '/health', '/'];
    if (publicPaths.some((path) => req.path.startsWith(path))) {
      this.logger.debug(`公共路径，跳过租户检查: path=${req.path}`);
      return next();
    }

    // 其他路径必须提供tenantId
    this.logger.warn(`缺少租户ID: path=${req.path}, headers=${JSON.stringify(req.headers)}`);
    throw new UnauthorizedException('缺少租户ID，请在header中提供X-Tenant-ID或通过JWT认证');
  }
}
