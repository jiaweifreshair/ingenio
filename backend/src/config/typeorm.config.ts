/**
 * TypeORM数据源配置
 * 用于运行迁移和CLI命令
 */

import { DataSource, DataSourceOptions } from 'typeorm';
import { ConfigService } from '@nestjs/config';
import * as dotenv from 'dotenv';

// 加载环境变量
dotenv.config();

/**
 * 创建TypeORM数据源配置
 */
export function createTypeOrmConfig(configService?: ConfigService): DataSourceOptions {
  const getEnv = (key: string, defaultValue?: string): string => {
    if (configService) {
      return configService.get<string>(key, defaultValue || '');
    }
    return process.env[key] || defaultValue || '';
  };

  return {
    type: 'postgres',
    host: getEnv('DB_HOST', 'localhost'),
    port: parseInt(getEnv('DB_PORT', '5432'), 10),
    username: getEnv('DB_USERNAME', 'ingenio'),
    password: getEnv('DB_PASSWORD', ''),
    database: getEnv('DB_DATABASE', 'ingenio_creator'),
    entities: [__dirname + '/../entities/*.entity{.ts,.js}'],
    migrations: [__dirname + '/../migrations/*{.ts,.js}'],
    synchronize: getEnv('DB_SYNCHRONIZE', 'false') === 'true',
    logging: getEnv('DB_LOGGING', 'false') === 'true',
    migrationsRun: false, // 不自动运行迁移，由CLI控制
    ssl: getEnv('DB_SSL_ENABLED', 'false') === 'true' ? {
      rejectUnauthorized: getEnv('DB_SSL_REJECT_UNAUTHORIZED', 'true') === 'true',
    } : false,
  };
}

/**
 * 默认数据源（用于CLI）
 */
export const AppDataSource = new DataSource(createTypeOrmConfig());
