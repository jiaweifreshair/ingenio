/**
 * AppSpec实体
 * 存储应用规范的主体信息
 */

import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
  OneToMany,
} from 'typeorm';
import { AppSpecVersionEntity } from './app-spec-version.entity';

/**
 * AppSpec实体
 */
@Entity('app_specs')
@Index(['tenantId', 'createdAt'])
@Index(['tenantId', 'status'])
export class AppSpecEntity {
  /**
   * 主键ID
   */
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  /**
   * 租户ID
   */
  @Column({ type: 'varchar', length: 100, name: 'tenant_id' })
  @Index()
  tenantId!: string;

  /**
   * 用户ID
   */
  @Column({ type: 'varchar', length: 100, name: 'user_id' })
  @Index()
  userId!: string;

  /**
   * AppSpec名称
   */
  @Column({ type: 'varchar', length: 200, nullable: true })
  name?: string;

  /**
   * 描述
   */
  @Column({ type: 'text', nullable: true })
  description?: string;

  /**
   * 当前版本号
   */
  @Column({ type: 'varchar', length: 50, name: 'current_version', default: '1.0.0' })
  currentVersion!: string;

  /**
   * 状态：draft | published | archived
   */
  @Column({
    type: 'enum',
    enum: ['draft', 'published', 'archived'],
    default: 'draft',
  })
  status!: 'draft' | 'published' | 'archived';

  /**
   * 原始需求描述
   */
  @Column({ type: 'text', name: 'requirement_text', nullable: true })
  requirementText?: string;

  /**
   * 项目类型
   */
  @Column({ type: 'varchar', length: 50, name: 'project_type', nullable: true })
  projectType?: string;

  /**
   * 元数据（JSON）
   */
  @Column({ type: 'jsonb', nullable: true })
  metadata?: Record<string, any>;

  /**
   * 创建时间
   */
  @CreateDateColumn({ name: 'created_at' })
  createdAt!: Date;

  /**
   * 更新时间
   */
  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt!: Date;

  /**
   * 发布时间
   */
  @Column({ type: 'timestamp', name: 'published_at', nullable: true })
  publishedAt?: Date;

  /**
   * 归档时间
   */
  @Column({ type: 'timestamp', name: 'archived_at', nullable: true })
  archivedAt?: Date;

  /**
   * 版本历史（一对多关系）
   */
  @OneToMany(() => AppSpecVersionEntity, (version) => version.appSpec)
  versions?: AppSpecVersionEntity[];
}
