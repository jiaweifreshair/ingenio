import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSpecEntity } from '../../entities/app-spec.entity';

@Injectable()
export class ProjectService {
  constructor(
    @InjectRepository(AppSpecEntity)
    private readonly appSpecRepo: Repository<AppSpecEntity>,
  ) {}

  async getStats(userId: string, tenantId: string) {
    const total = await this.appSpecRepo.count({ where: { userId, tenantId } });
    // Mock other stats for now
    return {
      totalProjects: total,
      monthlyNewProjects: 0,
      generatingTasks: 0,
      publishedProjects: 0,
      draftProjects: total,
      archivedProjects: 0,
    };
  }

  async listProjects(
    userId: string,
    tenantId: string,
    options: { current: number; size: number; status?: string; keyword?: string },
  ) {
    const { current = 1, size = 12, status, keyword } = options;
    const skip = (current - 1) * size;

    const query = this.appSpecRepo.createQueryBuilder('appSpec')
      .where('appSpec.userId = :userId', { userId })
      .andWhere('appSpec.tenantId = :tenantId', { tenantId });

    if (status) {
        // Map 'draft' to whatever status we use in DB, or just ignore if mismatch
       query.andWhere('appSpec.status = :status', { status });
    }

    if (keyword) {
      query.andWhere('(appSpec.name LIKE :keyword OR appSpec.description LIKE :keyword)', { keyword: `%${keyword}%` });
    }

    query.orderBy('appSpec.updatedAt', 'DESC')
         .skip(skip)
         .take(size);

    const [items, total] = await query.getManyAndCount();

    // Map AppSpecEntity to Project DTO
    const records = items.map(item => ({
      id: item.id,
      tenantId: item.tenantId,
      userId: item.userId,
      name: item.name || 'Untitled Project',
      description: item.description || '',
      status: item.status || 'draft',
      visibility: 'private',
      createdAt: item.createdAt.toISOString(),
      updatedAt: item.updatedAt.toISOString(),
      appSpecId: item.id,
      coverImageUrl: '', // Placeholder
      // ... other fields
    }));

    return {
      records,
      total,
      size,
      current,
      pages: Math.ceil(total / size),
      hasNext: current * size < total,
      hasPrevious: current > 1,
    };
  }
}
