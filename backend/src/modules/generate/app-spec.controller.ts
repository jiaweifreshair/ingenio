import { Controller, Get, Param, UseGuards, HttpCode, HttpStatus } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { AppSpecService } from './app-spec.service';
import { JwtAuthGuard } from '../../common/guards/jwt-auth.guard';

@ApiTags('AppSpecs')
@Controller('api/v1/appspecs')
export class AppSpecController {
  constructor(private readonly appSpecService: AppSpecService) {}

  @Get(':id')
  @HttpCode(HttpStatus.OK)
  @UseGuards(JwtAuthGuard)
  @ApiOperation({ summary: 'Get AppSpec details' })
  @ApiResponse({ status: 200, description: 'Return AppSpec details.' })
  @ApiResponse({ status: 404, description: 'AppSpec not found.' })
  async findOne(@Param('id') id: string) {
    const data = await this.appSpecService.findOne(id);
    return {
      success: true,
      data,
    };
  }
}
