/**
 * 应用主模块
 * 配置所有功能模块、中间件、拦截器等
 */

import { Module, NestModule, MiddlewareConsumer } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { APP_GUARD } from '@nestjs/core';
import { GenerateController } from './modules/generate/generate.controller';
import { GenerateService } from './modules/generate/generate.service';
import { AppSpecController } from './modules/generate/app-spec.controller';
import { AppSpecService } from './modules/generate/app-spec.service';
import { PlanRoutingController } from './modules/plan-routing/plan-routing.controller';
import { PlanRoutingService } from './modules/plan-routing/plan-routing.service';
import { ProjectController } from './modules/project/project.controller';
import { ProjectService } from './modules/project/project.service';
import { AuthModule } from './modules/auth/auth.module';
import { createTypeOrmConfig } from './config/typeorm.config';
import { AppSpecEntity, AppSpecVersionEntity, UserEntity } from './entities';
import { TenantMiddleware } from './common/middleware/tenant.middleware';
import { PermissionGuard } from './common/guards/permission.guard';

/**
 * 应用根模块
 */
@Module({
  imports: [
    // 配置模块 - 加载环境变量
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env.local', '.env'],
    }),

    // 数据库模块 - TypeORM配置
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => createTypeOrmConfig(configService),
      inject: [ConfigService],
    }),

    // 注册Entity到模块
    TypeOrmModule.forFeature([AppSpecEntity, AppSpecVersionEntity, UserEntity]),

    // 认证模块
    AuthModule,
  ],
  controllers: [GenerateController, AppSpecController, PlanRoutingController, ProjectController],
  providers: [
    GenerateService,
    AppSpecService,
    PlanRoutingService,
    ProjectService,
    // 提供IModelProvider（OpenAI实现或Mock）
    {
      provide: 'IModelProvider',
      useValue: {
        getProviderName: () => 'openai',
        chat: async () => ({
          content: JSON.stringify({
            intent: 'DESIGN_FROM_SCRATCH',
            confidence: 0.9,
            keywords: ['app', 'design'],
          }),
        }),
        healthCheck: async () => true,
      },
    },
    // 全局权限守卫
    {
      provide: APP_GUARD,
      useClass: PermissionGuard,
    },
  ],
})
export class AppModule implements NestModule {
  /**
   * 配置中间件
   * 应用租户隔离中间件到所有路由
   */
  configure(consumer: MiddlewareConsumer) {
    consumer.apply(TenantMiddleware).forRoutes('*');
  }
}
