import { Controller, Get, Query, Request, UseGuards } from '@nestjs/common';
import { ApiTags, ApiOperation } from '@nestjs/swagger';
import { ProjectService } from './project.service';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';

@ApiTags('Projects')
@Controller('api/v1/projects')
export class ProjectController {
  constructor(private readonly projectService: ProjectService) {}

  @Get('stats')
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Get project statistics' })
  async getStats(@Request() req: any) {
    return {
      success: true,
      data: await this.projectService.getStats(req.user.userId, req.user.tenantId),
    };
  }

  @Get()
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'List projects' })
  async listProjects(
    @Request() req: any,
    @Query('current') current: number,
    @Query('size') size: number,
    @Query('status') status: string,
    @Query('keyword') keyword: string,
  ) {
    const result = await this.projectService.listProjects(req.user.userId, req.user.tenantId, {
      current: Number(current) || 1,
      size: Number(size) || 12,
      status,
      keyword,
    });

    return {
      success: true,
      data: result,
    };
  }
}
