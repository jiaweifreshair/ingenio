/**
 * AppSpec版本实体
 * 存储AppSpec的历史版本快照
 */

import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  Index,
  ManyToOne,
  JoinColumn,
} from 'typeorm';
import { AppSpecEntity } from './app-spec.entity';
import { AppSpec } from '@shared/types/appspec.types';

/**
 * AppSpec版本实体
 */
@Entity('app_spec_versions')
@Index(['appId', 'version'])
@Index(['tenantId', 'createdAt'])
@Index(['source', 'agentType'])
export class AppSpecVersionEntity {
  /**
   * 主键ID
   */
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  /**
   * 关联的AppSpec ID
   */
  @Column({ type: 'uuid', name: 'app_id' })
  @Index()
  appId!: string;

  /**
   * 租户ID（冗余，便于查询）
   */
  @Column({ type: 'varchar', length: 100, name: 'tenant_id' })
  @Index()
  tenantId!: string;

  /**
   * 版本号
   */
  @Column({ type: 'varchar', length: 50 })
  version!: string;

  /**
   * AppSpec完整内容（JSON）
   */
  @Column({ type: 'jsonb', name: 'content_json' })
  contentJson!: AppSpec;

  /**
   * 来源：plan | exec | validate | manual
   */
  @Column({
    type: 'enum',
    enum: ['plan', 'exec', 'validate', 'manual'],
    default: 'exec',
  })
  source!: 'plan' | 'exec' | 'validate' | 'manual';

  /**
   * Agent类型（如果是Agent生成的）
   */
  @Column({ type: 'varchar', length: 50, name: 'agent_type', nullable: true })
  agentType?: string;

  /**
   * 变更描述
   */
  @Column({ type: 'text', name: 'change_description', nullable: true })
  changeDescription?: string;

  /**
   * 生成元数据（JSON）
   */
  @Column({ type: 'jsonb', name: 'generation_metadata', nullable: true })
  generationMetadata?: Record<string, any>;

  /**
   * 校验结果（JSON）
   */
  @Column({ type: 'jsonb', name: 'validation_result', nullable: true })
  validationResult?: {
    isValid: boolean;
    errors: any[];
    warnings: any[];
    score: number;
  };

  /**
   * 创建时间
   */
  @CreateDateColumn({ name: 'created_at' })
  createdAt!: Date;

  /**
   * 创建者ID
   */
  @Column({ type: 'varchar', length: 100, name: 'created_by', nullable: true })
  createdBy?: string;

  /**
   * 关联的AppSpec实体（多对一关系）
   */
  @ManyToOne(() => AppSpecEntity, (appSpec) => appSpec.versions, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'app_id' })
  appSpec?: AppSpecEntity;
}
