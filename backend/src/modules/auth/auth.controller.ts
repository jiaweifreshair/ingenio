/**
 * 认证控制器
 * 处理登录、注册、刷新token等HTTP请求
 */
import {
  Controller,
  Post,
  Body,
  HttpCode,
  HttpStatus,
  Get,
  UseGuards,
  Request,
} from '@nestjs/common';
import { AuthService, LoginDto, RegisterDto } from './auth.service';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';

/**
 * 刷新token DTO
 */
class RefreshTokenDto {
  /** 刷新token */
  refreshToken!: string;
}

/**
 * 认证控制器类
 */
@Controller('api/auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  /**
   * 用户登录
   * POST /api/auth/login
   *
   * @param loginDto - 登录信息
   * @returns 认证响应（tokens + 用户信息）
   */
  @Post('login')
  @HttpCode(HttpStatus.OK)
  async login(@Body() loginDto: LoginDto) {
    return await this.authService.login(loginDto);
  }

  /**
   * 用户注册
   * POST /api/auth/register
   *
   * @param registerDto - 注册信息
   * @returns 认证响应（tokens + 用户信息）
   */
  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  async register(@Body() registerDto: RegisterDto) {
    return await this.authService.register(registerDto);
  }

  /**
   * 刷新访问token
   * POST /api/auth/refresh
   *
   * @param refreshTokenDto - 刷新token
   * @returns 新的访问token
   */
  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  async refresh(@Body() refreshTokenDto: RefreshTokenDto) {
    return await this.authService.refreshAccessToken(refreshTokenDto.refreshToken);
  }

  /**
   * 获取当前用户信息
   * GET /api/auth/me
   * 需要JWT认证
   *
   * @param req - HTTP请求对象（包含user信息）
   * @returns 当前用户信息
   */
  @Get('me')
  @UseGuards(JwtAuthGuard)
  async getCurrentUser(@Request() req: any) {
    const userId = req.user.userId;
    const user = await this.authService.getUserById(userId);

    if (!user) {
      return null;
    }

    // 返回安全的用户信息（不包含密码哈希）
    return {
      id: user.id,
      username: user.username,
      email: user.email,
      role: user.role,
      tenantId: user.tenantId,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      permissions: user.permissions,
      status: user.status,
      lastLoginAt: user.lastLoginAt,
      createdAt: user.createdAt,
    };
  }
}
