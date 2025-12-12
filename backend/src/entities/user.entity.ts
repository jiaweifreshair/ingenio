/**
 * 用户实体
 * 管理用户认证信息和基本资料
 */
import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

/**
 * 用户实体类
 */
@Entity('users')
export class UserEntity {
  /**
   * 用户ID（UUID）
   */
  @PrimaryGeneratedColumn('uuid')
  id!: string;

  /**
   * 租户ID - 用于租户隔离
   */
  @Column({ name: 'tenant_id', type: 'varchar', length: 255 })
  @Index()
  tenantId!: string;

  /**
   * 用户名 - 唯一标识
   */
  @Column({ type: 'varchar', length: 255, unique: true })
  @Index()
  username!: string;

  /**
   * 邮箱地址 - 唯一标识
   */
  @Column({ type: 'varchar', length: 255, unique: true })
  @Index()
  email!: string;

  /**
   * 密码哈希值（bcrypt）
   */
  @Column({ name: 'password_hash', type: 'varchar', length: 255 })
  passwordHash!: string;

  /**
   * 用户角色
   * admin: 管理员
   * user: 普通用户
   * viewer: 只读用户
   */
  @Column({ type: 'varchar', length: 50, default: 'user' })
  role!: string;

  /**
   * 权限列表（JSON数组）
   */
  @Column({ type: 'jsonb', default: '[]' })
  permissions!: string[];

  /**
   * 用户状态
   * active: 激活
   * inactive: 未激活
   * suspended: 暂停
   */
  @Column({ type: 'varchar', length: 50, default: 'active' })
  status!: string;

  /**
   * 用户显示名称
   */
  @Column({ name: 'display_name', type: 'varchar', length: 255, nullable: true })
  displayName?: string;

  /**
   * 头像URL
   */
  @Column({ name: 'avatar_url', type: 'varchar', length: 500, nullable: true })
  avatarUrl?: string;

  /**
   * 最后登录时间
   */
  @Column({ name: 'last_login_at', type: 'timestamp', nullable: true })
  lastLoginAt?: Date;

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
   * 元数据（JSON）
   */
  @Column({ type: 'jsonb', default: '{}' })
  metadata!: Record<string, any>;
}
