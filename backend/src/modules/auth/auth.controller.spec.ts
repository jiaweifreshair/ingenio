/**
 * AuthController 单元测试
 */
import { Test, TestingModule } from '@nestjs/testing';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { UnauthorizedException } from '@nestjs/common';

describe('AuthController', () => {
  let controller: AuthController;

  // Mock AuthService
  const mockAuthService = {
    login: jest.fn(),
    register: jest.fn(),
    refreshAccessToken: jest.fn(),
    getUserById: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        {
          provide: AuthService,
          useValue: mockAuthService,
        },
      ],
    }).compile();

    controller = module.get<AuthController>(AuthController);

    // 清除所有mock
    jest.clearAllMocks();
  });

  it('应该正确初始化控制器', () => {
    expect(controller).toBeDefined();
  });

  describe('login', () => {
    it('应该成功登录', async () => {
      // Arrange
      const loginDto = {
        username: 'testuser',
        password: 'password123',
        tenantId: 'tenant-1',
      };

      const expectedResponse = {
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: 'user-1',
          username: 'testuser',
          email: 'test@example.com',
          role: 'user',
          tenantId: 'tenant-1',
        },
      };

      mockAuthService.login.mockResolvedValue(expectedResponse);

      // Act
      const result = await controller.login(loginDto);

      // Assert
      expect(result).toEqual(expectedResponse);
      expect(mockAuthService.login).toHaveBeenCalledWith(loginDto);
    });

    it('登录失败应该抛出异常', async () => {
      // Arrange
      const loginDto = {
        username: 'testuser',
        password: 'wrongpassword',
        tenantId: 'tenant-1',
      };

      mockAuthService.login.mockRejectedValue(
        new UnauthorizedException('用户名或密码错误'),
      );

      // Act & Assert
      await expect(controller.login(loginDto)).rejects.toThrow(UnauthorizedException);
      expect(mockAuthService.login).toHaveBeenCalledWith(loginDto);
    });
  });

  describe('register', () => {
    it('应该成功注册', async () => {
      // Arrange
      const registerDto = {
        username: 'newuser',
        email: 'new@example.com',
        password: 'password123',
        tenantId: 'tenant-1',
      };

      const expectedResponse = {
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: 'user-1',
          username: 'newuser',
          email: 'new@example.com',
          role: 'user',
          tenantId: 'tenant-1',
        },
      };

      mockAuthService.register.mockResolvedValue(expectedResponse);

      // Act
      const result = await controller.register(registerDto);

      // Assert
      expect(result).toEqual(expectedResponse);
      expect(mockAuthService.register).toHaveBeenCalledWith(registerDto);
    });
  });

  describe('refreshAccessToken', () => {
    it('应该成功刷新token', async () => {
      // Arrange
      const refreshTokenDto = {
        refreshToken: 'valid-refresh-token',
      };

      const expectedResponse = {
        accessToken: 'new-access-token',
      };

      mockAuthService.refreshAccessToken.mockResolvedValue(expectedResponse);

      // Act
      const result = await controller.refresh(refreshTokenDto);

      // Assert
      expect(result).toEqual(expectedResponse);
      expect(mockAuthService.refreshAccessToken).toHaveBeenCalledWith('valid-refresh-token');
    });
  });

  describe('getCurrentUser', () => {
    it('应该返回当前用户信息', async () => {
      // Arrange
      const mockUser = {
        id: 'user-1',
        username: 'testuser',
        email: 'test@example.com',
        role: 'user',
        tenantId: 'tenant-1',
        displayName: 'Test User',
        avatarUrl: 'https://example.com/avatar.jpg',
        permissions: ['appspec:generate'],
        status: 'active',
        lastLoginAt: new Date(),
        createdAt: new Date(),
      };

      const mockRequest = {
        user: {
          userId: 'user-1',
        },
      };

      mockAuthService.getUserById.mockResolvedValue(mockUser);

      // Act
      const result = await controller.getCurrentUser(mockRequest);

      // Assert
      expect(result).toEqual(mockUser);
      expect(mockAuthService.getUserById).toHaveBeenCalledWith('user-1');
    });

    it('用户不存在时应该返回null', async () => {
      // Arrange
      const mockRequest = {
        user: {
          userId: 'non-existent-user',
        },
      };

      mockAuthService.getUserById.mockResolvedValue(null);

      // Act
      const result = await controller.getCurrentUser(mockRequest);

      // Assert
      expect(result).toBeNull();
      expect(mockAuthService.getUserById).toHaveBeenCalledWith('non-existent-user');
    });
  });
});
