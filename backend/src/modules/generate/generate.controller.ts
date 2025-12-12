/**
 * Generate Controller
 * 提供AppSpec生成的HTTP API接口
 */

import {
  Controller,
  Post,
  Body,
  HttpCode,
  HttpStatus,
  Logger,
  UseGuards,
  Request,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { GenerateService } from './generate.service';
import { PlanAgentInput, AgentResponse } from '@shared/types/agent-io.types';
import { AppSpec } from '@shared/types/appspec.types';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';
import { Permissions } from '../../common/decorators/permissions.decorator';

/**
 * 生成请求DTO
 */
class GenerateAppSpecDto {
  /** 用户需求描述 */
  requirement!: string;

  /** 租户ID */
  tenantId!: string;

  /** 用户ID */
  userId!: string;

  /** 项目类型（可选） */
  projectType?: string;

  /** 业务约束（可选） */
  constraints?: string[];
}

/**
 * Generate控制器
 */
@ApiTags('Generate')
@Controller('api/generate')
export class GenerateController {
  private readonly logger = new Logger(GenerateController.name);

  constructor(private readonly generateService: GenerateService) {}

  /**
   * 生成AppSpec
   * 接收用户需求描述，返回生成的AppSpec
   * 需要JWT认证和appspec:generate权限
   */
  @Post('appspec')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @Permissions('appspec:generate')
  @ApiOperation({
    summary: '生成AppSpec',
    description: '根据自然语言需求描述，生成结构化的AppSpec应用规范',
  })
  @ApiResponse({
    status: 200,
    description: 'AppSpec生成成功',
  })
  @ApiResponse({
    status: 400,
    description: '请求参数错误',
  })
  @ApiResponse({
    status: 401,
    description: '未授权',
  })
  @ApiResponse({
    status: 403,
    description: '没有权限',
  })
  @ApiResponse({
    status: 500,
    description: '服务器内部错误',
  })
  async generateAppSpec(
    @Body() dto: GenerateAppSpecDto,
    @Request() req: any,
  ): Promise<AgentResponse<AppSpec>> {
    // 从JWT token中获取tenantId和userId
    const tenantId = req.user.tenantId;
    const userId = req.user.userId;
    this.logger.log(
      `接收到生成请求: tenantId=${dto.tenantId}, ` +
        `requirementLength=${dto.requirement.length}`,
    );

    // 验证输入
    if (!dto.requirement || dto.requirement.trim().length === 0) {
      return {
        success: false,
        error: {
          code: 'INVALID_INPUT',
          message: '需求描述不能为空',
          recoverable: true,
        },
        metadata: {
          requestId: 'n/a',
          agentType: 'execute',
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      };
    }

    if (dto.requirement.length < 10) {
      return {
        success: false,
        error: {
          code: 'INVALID_INPUT',
          message: '需求描述过短，请至少提供10个字符的描述',
          recoverable: true,
        },
        metadata: {
          requestId: 'n/a',
          agentType: 'execute',
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      };
    }

    // 构造PlanAgentInput
    // 使用认证用户的tenantId和userId，而非请求体中的值（安全考虑）
    const input: PlanAgentInput = {
      requirement: dto.requirement,
      context: {
        tenantId,
        userId,
        projectType: dto.projectType as any,
        constraints: dto.constraints,
      },
    };

    // 调用生成服务
    const result = await this.generateService.generateAppSpec(input);

    if (result.success) {
      this.logger.log(
        `生成成功: requestId=${result.metadata.requestId}, ` +
          `latency=${result.metadata.latencyMs}ms`,
      );
    } else {
      this.logger.error(
        `生成失败: requestId=${result.metadata.requestId}, ` +
          `error=${result.error?.message}`,
      );
    }

    return result;
  }
}
