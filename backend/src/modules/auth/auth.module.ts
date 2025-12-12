/**
 * 认证模块
 * 配置JWT、Passport策略和认证相关服务
 */
import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtStrategy } from '../../common/strategies/jwt.strategy';
import { UserEntity } from '../../entities/user.entity';

/**
 * 认证模块类
 */
@Module({
  imports: [
    // 注册UserEntity
    TypeOrmModule.forFeature([UserEntity]),

    // 配置Passport
    PassportModule.register({
      defaultStrategy: 'jwt',
    }),

    // 配置JWT模块
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: async (configService: ConfigService) => ({
        secret: configService.get<string>('JWT_SECRET') || 'dev-secret-key-change-in-production',
        signOptions: {
          expiresIn: '15m', // 默认15分钟过期
        },
      }),
      inject: [ConfigService],
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService, JwtStrategy],
  exports: [AuthService, JwtStrategy, PassportModule],
})
export class AuthModule {}
