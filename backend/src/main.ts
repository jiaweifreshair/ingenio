/**
 * NestJS应用入口文件
 */

import { NestFactory } from '@nestjs/core';
import { ValidationPipe, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';

/**
 * 启动应用
 */
async function bootstrap() {
  const logger = new Logger('Bootstrap');

  // 创建NestJS应用实例
  const app = await NestFactory.create(AppModule, {
    logger: ['log', 'error', 'warn', 'debug', 'verbose'],
  });

  // 获取配置服务
  const configService = app.get(ConfigService);

  // 启用CORS
  app.enableCors({
    origin: true,
    credentials: true,
  });

  // 全局验证管道
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  // API全局前缀
  app.setGlobalPrefix('api');

  // Swagger文档配置
  const swaggerConfig = new DocumentBuilder()
    .setTitle('秒构AI API')
    .setDescription('Spec Agents系统 - AI驱动的应用生成服务')
    .setVersion('1.0.0')
    .addTag('Generate', 'AppSpec生成相关接口')
    .build();

  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('api/docs', app, document);

  // 获取端口配置
  const port = configService.get<number>('PORT', 3001);

  // 启动应用
  await app.listen(port);

  logger.log(`应用已启动: http://localhost:${port}`);
  logger.log(`Swagger文档: http://localhost:${port}/api/docs`);
  logger.log(`Workers服务: ${configService.get('WORKERS_BASE_URL', 'http://localhost:8000')}`);
}

bootstrap();
