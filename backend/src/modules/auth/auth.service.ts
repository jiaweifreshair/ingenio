/**
 * 认证服务
 * 处理用户登录、注册、token生成等认证相关业务逻辑
 */
import { Injectable, UnauthorizedException, ConflictException, Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { UserEntity } from '../../entities/user.entity';
import { JwtPayload } from '../../common/strategies/jwt.strategy';

/**
 * 登录DTO
 */
export interface LoginDto {
  /** 用户名或邮箱 */
  username: string;
  /** 密码 */
  password: string;
  /** 租户ID */
  tenantId: string;
}

/**
 * 注册DTO
 */
export interface RegisterDto {
  /** 用户名 */
  username: string;
  /** 邮箱 */
  email: string;
  /** 密码 */
  password: string;
  /** 租户ID */
  tenantId: string;
  /** 显示名称（可选） */
  displayName?: string;
}

/**
 * 认证响应
 */
export interface AuthResponse {
  /** 访问token */
  accessToken: string;
  /** 刷新token */
  refreshToken: string;
  /** 用户信息 */
  user: {
    id: string;
    username: string;
    email: string;
    role: string;
    tenantId: string;
  };
}

/**
 * 认证服务类
 */
@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  /**
   * bcrypt加密轮数
   */
  private readonly SALT_ROUNDS = 10;

  constructor(
    @InjectRepository(UserEntity)
    private readonly userRepo: Repository<UserEntity>,
    private readonly jwtService: JwtService,
  ) {}

  /**
   * 用户登录
   *
   * @param loginDto - 登录信息
   * @returns 认证响应（包含tokens和用户信息）
   */
  async login(loginDto: LoginDto): Promise<AuthResponse> {
    const { username, password, tenantId } = loginDto;

    // 查找用户（支持用户名或邮箱登录）
    const user = await this.userRepo.findOne({
      where: [
        { username, tenantId },
        { email: username, tenantId },
      ],
    });

    if (!user) {
      this.logger.warn(`登录失败: 用户不存在 - username=${username}, tenantId=${tenantId}`);
      throw new UnauthorizedException('用户名或密码错误');
    }

    // 验证密码
    const isPasswordValid = await bcrypt.compare(password, user.passwordHash);
    if (!isPasswordValid) {
      this.logger.warn(`登录失败: 密码错误 - userId=${user.id}`);
      throw new UnauthorizedException('用户名或密码错误');
    }

    // 检查用户状态
    if (user.status !== 'active') {
      this.logger.warn(`登录失败: 用户状态异常 - userId=${user.id}, status=${user.status}`);
      throw new UnauthorizedException('用户账号已被禁用');
    }

    // 更新最后登录时间
    user.lastLoginAt = new Date();
    await this.userRepo.save(user);

    // 生成tokens
    const tokens = await this.generateTokens(user);

    this.logger.log(`用户登录成功: userId=${user.id}, tenantId=${tenantId}`);

    return {
      ...tokens,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        role: user.role,
        tenantId: user.tenantId,
      },
    };
  }

  /**
   * 用户注册
   *
   * @param registerDto - 注册信息
   * @returns 认证响应（包含tokens和用户信息）
   */
  async register(registerDto: RegisterDto): Promise<AuthResponse> {
    const { username, email, password, tenantId, displayName } = registerDto;

    // 检查用户名是否已存在
    const existingUsername = await this.userRepo.findOne({
      where: { username },
    });
    if (existingUsername) {
      throw new ConflictException('用户名已存在');
    }

    // 检查邮箱是否已存在
    const existingEmail = await this.userRepo.findOne({
      where: { email },
    });
    if (existingEmail) {
      throw new ConflictException('邮箱已被注册');
    }

    // 密码哈希
    const passwordHash = await bcrypt.hash(password, this.SALT_ROUNDS);

    // 创建用户
    const user = this.userRepo.create({
      username,
      email,
      passwordHash,
      tenantId,
      displayName: displayName || username,
      role: 'user',
      permissions: [],
      status: 'active',
    });

    await this.userRepo.save(user);

    this.logger.log(`用户注册成功: userId=${user.id}, tenantId=${tenantId}`);

    // 生成tokens
    const tokens = await this.generateTokens(user);

    return {
      ...tokens,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        role: user.role,
        tenantId: user.tenantId,
      },
    };
  }

  /**
   * 生成访问token和刷新token
   *
   * @param user - 用户实体
   * @returns tokens对象
   */
  private async generateTokens(
    user: UserEntity,
  ): Promise<{ accessToken: string; refreshToken: string }> {
    const payload: JwtPayload = {
      sub: user.id,
      username: user.username,
      tenantId: user.tenantId,
      role: user.role,
      permissions: user.permissions,
    };

    // 生成访问token（15分钟过期）
    const accessToken = this.jwtService.sign(payload, {
      expiresIn: '15m',
    });

    // 生成刷新token（7天过期）
    const refreshToken = this.jwtService.sign(payload, {
      expiresIn: '7d',
    });

    return {
      accessToken,
      refreshToken,
    };
  }

  /**
   * 刷新访问token
   *
   * @param refreshToken - 刷新token
   * @returns 新的访问token
   */
  async refreshAccessToken(refreshToken: string): Promise<{ accessToken: string }> {
    try {
      // 验证刷新token
      const payload = this.jwtService.verify<JwtPayload>(refreshToken);

      // 查找用户
      const user = await this.userRepo.findOne({
        where: { id: payload.sub },
      });

      if (!user || user.status !== 'active') {
        throw new UnauthorizedException('用户不存在或已被禁用');
      }

      // 生成新的访问token
      const newPayload: JwtPayload = {
        sub: user.id,
        username: user.username,
        tenantId: user.tenantId,
        role: user.role,
        permissions: user.permissions,
      };

      const accessToken = this.jwtService.sign(newPayload, {
        expiresIn: '15m',
      });

      return { accessToken };
    } catch (error: any) {
      this.logger.error(`刷新token失败: ${error.message}`);
      throw new UnauthorizedException('无效的刷新token');
    }
  }

  /**
   * 根据用户ID获取用户信息
   *
   * @param userId - 用户ID
   * @returns 用户实体
   */
  async getUserById(userId: string): Promise<UserEntity | null> {
    return await this.userRepo.findOne({
      where: { id: userId },
    });
  }
}
