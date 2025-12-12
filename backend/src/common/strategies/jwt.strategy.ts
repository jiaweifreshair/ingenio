/**
 * JWT策略
 * 用于验证JWT token并解析用户信息
 */
import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { ConfigService } from '@nestjs/config';

/**
 * JWT Payload接口
 */
export interface JwtPayload {
  /** 用户ID */
  sub: string;
  /** 用户名 */
  username: string;
  /** 租户ID */
  tenantId: string;
  /** 用户角色 */
  role: string;
  /** 权限列表 */
  permissions: string[];
  /** 签发时间 */
  iat?: number;
  /** 过期时间 */
  exp?: number;
}

/**
 * JWT策略类
 * 继承自Passport JWT策略
 */
@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(configService: ConfigService) {
    super({
      // 从Authorization header中提取Bearer token
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      // 不忽略过期token
      ignoreExpiration: false,
      // JWT签名密钥
      secretOrKey: configService.get<string>('JWT_SECRET') || 'dev-secret-key-change-in-production',
    });
  }

  /**
   * 验证JWT payload并返回用户信息
   * 该方法在token验证成功后自动调用
   *
   * @param payload - JWT解析后的payload
   * @returns 用户信息对象
   */
  async validate(payload: JwtPayload) {
    // 验证必需字段
    if (!payload.sub || !payload.tenantId) {
      throw new UnauthorizedException('无效的token payload');
    }

    // 返回用户信息，将被注入到request.user
    return {
      userId: payload.sub,
      username: payload.username,
      tenantId: payload.tenantId,
      role: payload.role,
      permissions: payload.permissions || [],
    };
  }
}
