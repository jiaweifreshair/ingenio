import {
  Controller,
  Post,
  Query,
  Body,
  Request,
  Param,
  Get,
  UseGuards,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import { ApiTags, ApiOperation } from '@nestjs/swagger';
import { PlanRoutingService } from './plan-routing.service';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';

class RouteRequirementDto {
  userRequirement!: string;
  tenantId?: string;
  userId?: string;
}

@ApiTags('PlanRouting')
@Controller('api/v2/plan-routing')
export class PlanRoutingController {
  constructor(private readonly planRoutingService: PlanRoutingService) {}

  @Post('route')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Route user requirement to intent and plan' })
  async routeRequirement(@Body() dto: RouteRequirementDto, @Request() req: any) {
    // Prefer authenticated user info
    const userId = req.user.userId || dto.userId;
    const tenantId = req.user.tenantId || dto.tenantId;
    
    return await this.planRoutingService.routeRequirement(
      dto.userRequirement,
      userId,
      tenantId
    );
  }

  @Post(':id/select-style')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Select style for the app plan' })
  async selectStyle(
    @Param('id') id: string,
    @Query('styleId') styleId: string,
  ) {
    return await this.planRoutingService.selectStyle(id, styleId);
  }

  @Post(':id/confirm-design')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Confirm design and proceed to execution' })
  async confirmDesign(@Param('id') id: string) {
    return await this.planRoutingService.confirmDesign(id);
  }

  @Get('history')
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Get user history' })
  async getHistory(@Request() req: any) {
    return await this.planRoutingService.getHistory(req.user.userId, req.user.tenantId);
  }
}
