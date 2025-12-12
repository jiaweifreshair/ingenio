/**
 * WorkersClient模块
 * 提供与Python Workers服务通信的HTTP客户端
 */

import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { WorkersClientService } from './workers-client.service';

@Module({
  imports: [ConfigModule],
  providers: [WorkersClientService],
  exports: [WorkersClientService],
})
export class WorkersClientModule {}
