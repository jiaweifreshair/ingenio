import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { AppSpecEntity } from '../../entities/app-spec.entity';

@Injectable()
export class AppSpecService {
  constructor(
    @InjectRepository(AppSpecEntity)
    private readonly appSpecRepo: Repository<AppSpecEntity>,
  ) {}

  async findOne(id: string) {
    const appSpec = await this.appSpecRepo.findOne({ where: { id } });
    if (!appSpec) {
      throw new NotFoundException(`AppSpec with ID ${id} not found`);
    }
    
    // Transform to DTO if needed, but for now return entity with specContent parsing if it's stored as string
    // The entity definition usually handles JSON columns if using postgres.
    // Let's assume typeorm handles it.
    return appSpec;
  }
}
