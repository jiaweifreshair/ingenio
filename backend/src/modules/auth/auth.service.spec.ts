/**
 * AuthService 单元测试
 */
import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { getRepositoryToken } from '@nestjs/typeorm';
import { UnauthorizedException, ConflictException } from '@nestjs/common';
import { AuthService } from './auth.service';
import { UserEntity } from '../../entities/user.entity';
import * as bcrypt from 'bcrypt';

// Mock bcrypt
jest.mock('bcrypt');

describe('AuthService', () => {
  let service: AuthService;

  // Mock用户数据
  const mockUser: UserEntity = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    tenantId: 'tenant-1',
    username: 'testuser',
    email: 'test@example.com',
    passwordHash: '$2b$10$hashedpassword',
    role: 'user',
    permissions: ['appspec:generate'],
    status: 'active',
    displayName: 'Test User',
    avatarUrl: undefined,
    lastLoginAt: undefined,
    createdAt: new Date(),
    updatedAt: new Date(),
    metadata: {},
  };

  // Mock Repository
  const mockRepository = {
    findOne: jest.fn(),
    create: jest.fn(),
    save: jest.fn(),
  };

  // Mock JwtService
  const mockJwtService = {
    sign: jest.fn(),
    verify: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: getRepositoryToken(UserEntity),
          useValue: mockRepository,
        },
        {
          provide: JwtService,
          useValue: mockJwtService,
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);

    // 清除所有mock
    jest.clearAllMocks();
  });

  it('应该正确初始化服务', () => {
    expect(service).toBeDefined();
  });

  describe('login', () => {
    it('应该成功登录并返回tokens', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValue(mockUser);
      (bcrypt.compare as jest.Mock).mockResolvedValue(true);
      mockJwtService.sign.mockReturnValueOnce('access-token').mockReturnValueOnce('refresh-token');
      mockRepository.save.mockResolvedValue(mockUser);

      // Act
      const result = await service.login({
        username: 'testuser',
        password: 'password123',
        tenantId: 'tenant-1',
      });

      // Assert
      expect(result).toEqual({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: mockUser.id,
          username: mockUser.username,
          email: mockUser.email,
          role: mockUser.role,
          tenantId: mockUser.tenantId,
        },
      });
      expect(mockRepository.findOne).toHaveBeenCalled();
      expect(bcrypt.compare).toHaveBeenCalledWith('password123', mockUser.passwordHash);
      expect(mockJwtService.sign).toHaveBeenCalledTimes(2);
    });

    it('用户不存在时应该抛出UnauthorizedException', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValue(null);

      // Act & Assert
      await expect(
        service.login({
          username: 'nonexistent',
          password: 'password123',
          tenantId: 'tenant-1',
        }),
      ).rejects.toThrow(UnauthorizedException);
    });

    it('密码错误时应该抛出UnauthorizedException', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValue(mockUser);
      (bcrypt.compare as jest.Mock).mockResolvedValue(false);

      // Act & Assert
      await expect(
        service.login({
          username: 'testuser',
          password: 'wrongpassword',
          tenantId: 'tenant-1',
        }),
      ).rejects.toThrow(UnauthorizedException);
    });

    it('用户状态非active时应该抛出UnauthorizedException', async () => {
      // Arrange
      const inactiveUser = { ...mockUser, status: 'suspended' };
      mockRepository.findOne.mockResolvedValue(inactiveUser);
      (bcrypt.compare as jest.Mock).mockResolvedValue(true);

      // Act & Assert
      await expect(
        service.login({
          username: 'testuser',
          password: 'password123',
          tenantId: 'tenant-1',
        }),
      ).rejects.toThrow(UnauthorizedException);
    });
  });

  describe('register', () => {
    it('应该成功注册新用户', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValue(null); // 用户名和邮箱都不存在
      (bcrypt.hash as jest.Mock).mockResolvedValue('hashed-password');
      mockRepository.create.mockReturnValue(mockUser);
      mockRepository.save.mockResolvedValue(mockUser);
      mockJwtService.sign.mockReturnValueOnce('access-token').mockReturnValueOnce('refresh-token');

      // Act
      const result = await service.register({
        username: 'newuser',
        email: 'new@example.com',
        password: 'password123',
        tenantId: 'tenant-1',
      });

      // Assert
      expect(result).toEqual({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: mockUser.id,
          username: mockUser.username,
          email: mockUser.email,
          role: mockUser.role,
          tenantId: mockUser.tenantId,
        },
      });
      expect(bcrypt.hash).toHaveBeenCalledWith('password123', 10);
      expect(mockRepository.create).toHaveBeenCalled();
    });

    it('用户名已存在时应该抛出ConflictException', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValueOnce(mockUser); // 用户名已存在

      // Act & Assert
      await expect(
        service.register({
          username: 'testuser',
          email: 'new@example.com',
          password: 'password123',
          tenantId: 'tenant-1',
        }),
      ).rejects.toThrow(ConflictException);
    });

    it('邮箱已存在时应该抛出ConflictException', async () => {
      // Arrange
      mockRepository.findOne
        .mockResolvedValueOnce(null) // 用户名不存在
        .mockResolvedValueOnce(mockUser); // 邮箱已存在

      // Act & Assert
      await expect(
        service.register({
          username: 'newuser',
          email: 'test@example.com',
          password: 'password123',
          tenantId: 'tenant-1',
        }),
      ).rejects.toThrow(ConflictException);
    });
  });

  describe('refreshAccessToken', () => {
    it('应该成功刷新访问token', async () => {
      // Arrange
      const payload = {
        sub: mockUser.id,
        username: mockUser.username,
        tenantId: mockUser.tenantId,
        role: mockUser.role,
        permissions: mockUser.permissions,
      };
      mockJwtService.verify.mockReturnValue(payload);
      mockRepository.findOne.mockResolvedValue(mockUser);
      mockJwtService.sign.mockReturnValue('new-access-token');

      // Act
      const result = await service.refreshAccessToken('valid-refresh-token');

      // Assert
      expect(result).toEqual({
        accessToken: 'new-access-token',
      });
      expect(mockJwtService.verify).toHaveBeenCalledWith('valid-refresh-token');
    });

    it('无效的刷新token应该抛出UnauthorizedException', async () => {
      // Arrange
      mockJwtService.verify.mockImplementation(() => {
        throw new Error('Invalid token');
      });

      // Act & Assert
      await expect(service.refreshAccessToken('invalid-token')).rejects.toThrow(
        UnauthorizedException,
      );
    });

    it('用户不存在或被禁用时应该抛出UnauthorizedException', async () => {
      // Arrange
      const payload = {
        sub: mockUser.id,
        username: mockUser.username,
        tenantId: mockUser.tenantId,
        role: mockUser.role,
        permissions: mockUser.permissions,
      };
      mockJwtService.verify.mockReturnValue(payload);
      mockRepository.findOne.mockResolvedValue(null);

      // Act & Assert
      await expect(service.refreshAccessToken('valid-refresh-token')).rejects.toThrow(
        UnauthorizedException,
      );
    });
  });

  describe('getUserById', () => {
    it('应该返回用户信息', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValue(mockUser);

      // Act
      const result = await service.getUserById(mockUser.id);

      // Assert
      expect(result).toEqual(mockUser);
      expect(mockRepository.findOne).toHaveBeenCalledWith({
        where: { id: mockUser.id },
      });
    });

    it('用户不存在时应该返回null', async () => {
      // Arrange
      mockRepository.findOne.mockResolvedValue(null);

      // Act
      const result = await service.getUserById('non-existent-id');

      // Assert
      expect(result).toBeNull();
    });
  });
});
