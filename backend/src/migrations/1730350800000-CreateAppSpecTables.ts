/**
 * 数据库迁移：创建AppSpec相关表
 */

import { MigrationInterface, QueryRunner, Table, TableIndex, TableForeignKey } from 'typeorm';

export class CreateAppSpecTables1730350800000 implements MigrationInterface {
  name = 'CreateAppSpecTables1730350800000';

  /**
   * 执行迁移
   */
  public async up(queryRunner: QueryRunner): Promise<void> {
    // 创建app_specs表
    await queryRunner.createTable(
      new Table({
        name: 'app_specs',
        columns: [
          {
            name: 'id',
            type: 'uuid',
            isPrimary: true,
            generationStrategy: 'uuid',
            default: 'uuid_generate_v4()',
          },
          {
            name: 'tenant_id',
            type: 'varchar',
            length: '100',
            isNullable: false,
          },
          {
            name: 'user_id',
            type: 'varchar',
            length: '100',
            isNullable: false,
          },
          {
            name: 'name',
            type: 'varchar',
            length: '200',
            isNullable: true,
          },
          {
            name: 'description',
            type: 'text',
            isNullable: true,
          },
          {
            name: 'current_version',
            type: 'varchar',
            length: '50',
            default: "'1.0.0'",
          },
          {
            name: 'status',
            type: 'enum',
            enum: ['draft', 'published', 'archived'],
            default: "'draft'",
          },
          {
            name: 'requirement_text',
            type: 'text',
            isNullable: true,
          },
          {
            name: 'project_type',
            type: 'varchar',
            length: '50',
            isNullable: true,
          },
          {
            name: 'metadata',
            type: 'jsonb',
            isNullable: true,
          },
          {
            name: 'created_at',
            type: 'timestamp',
            default: 'CURRENT_TIMESTAMP',
          },
          {
            name: 'updated_at',
            type: 'timestamp',
            default: 'CURRENT_TIMESTAMP',
          },
          {
            name: 'published_at',
            type: 'timestamp',
            isNullable: true,
          },
          {
            name: 'archived_at',
            type: 'timestamp',
            isNullable: true,
          },
        ],
      }),
      true,
    );

    // 创建app_specs索引
    await queryRunner.createIndex(
      'app_specs',
      new TableIndex({
        name: 'IDX_app_specs_tenant_id',
        columnNames: ['tenant_id'],
      }),
    );

    await queryRunner.createIndex(
      'app_specs',
      new TableIndex({
        name: 'IDX_app_specs_user_id',
        columnNames: ['user_id'],
      }),
    );

    await queryRunner.createIndex(
      'app_specs',
      new TableIndex({
        name: 'IDX_app_specs_tenant_created',
        columnNames: ['tenant_id', 'created_at'],
      }),
    );

    await queryRunner.createIndex(
      'app_specs',
      new TableIndex({
        name: 'IDX_app_specs_tenant_status',
        columnNames: ['tenant_id', 'status'],
      }),
    );

    // 创建app_spec_versions表
    await queryRunner.createTable(
      new Table({
        name: 'app_spec_versions',
        columns: [
          {
            name: 'id',
            type: 'uuid',
            isPrimary: true,
            generationStrategy: 'uuid',
            default: 'uuid_generate_v4()',
          },
          {
            name: 'app_id',
            type: 'uuid',
            isNullable: false,
          },
          {
            name: 'tenant_id',
            type: 'varchar',
            length: '100',
            isNullable: false,
          },
          {
            name: 'version',
            type: 'varchar',
            length: '50',
            isNullable: false,
          },
          {
            name: 'content_json',
            type: 'jsonb',
            isNullable: false,
          },
          {
            name: 'source',
            type: 'enum',
            enum: ['plan', 'exec', 'validate', 'manual'],
            default: "'exec'",
          },
          {
            name: 'agent_type',
            type: 'varchar',
            length: '50',
            isNullable: true,
          },
          {
            name: 'change_description',
            type: 'text',
            isNullable: true,
          },
          {
            name: 'generation_metadata',
            type: 'jsonb',
            isNullable: true,
          },
          {
            name: 'validation_result',
            type: 'jsonb',
            isNullable: true,
          },
          {
            name: 'created_at',
            type: 'timestamp',
            default: 'CURRENT_TIMESTAMP',
          },
          {
            name: 'created_by',
            type: 'varchar',
            length: '100',
            isNullable: true,
          },
        ],
      }),
      true,
    );

    // 创建app_spec_versions索引
    await queryRunner.createIndex(
      'app_spec_versions',
      new TableIndex({
        name: 'IDX_app_spec_versions_app_id',
        columnNames: ['app_id'],
      }),
    );

    await queryRunner.createIndex(
      'app_spec_versions',
      new TableIndex({
        name: 'IDX_app_spec_versions_tenant_id',
        columnNames: ['tenant_id'],
      }),
    );

    await queryRunner.createIndex(
      'app_spec_versions',
      new TableIndex({
        name: 'IDX_app_spec_versions_app_version',
        columnNames: ['app_id', 'version'],
      }),
    );

    await queryRunner.createIndex(
      'app_spec_versions',
      new TableIndex({
        name: 'IDX_app_spec_versions_tenant_created',
        columnNames: ['tenant_id', 'created_at'],
      }),
    );

    await queryRunner.createIndex(
      'app_spec_versions',
      new TableIndex({
        name: 'IDX_app_spec_versions_source_agent',
        columnNames: ['source', 'agent_type'],
      }),
    );

    // 创建外键
    await queryRunner.createForeignKey(
      'app_spec_versions',
      new TableForeignKey({
        name: 'FK_app_spec_versions_app_id',
        columnNames: ['app_id'],
        referencedTableName: 'app_specs',
        referencedColumnNames: ['id'],
        onDelete: 'CASCADE',
      }),
    );
  }

  /**
   * 回滚迁移
   */
  public async down(queryRunner: QueryRunner): Promise<void> {
    // 删除外键
    await queryRunner.dropForeignKey('app_spec_versions', 'FK_app_spec_versions_app_id');

    // 删除索引
    await queryRunner.dropIndex('app_spec_versions', 'IDX_app_spec_versions_source_agent');
    await queryRunner.dropIndex('app_spec_versions', 'IDX_app_spec_versions_tenant_created');
    await queryRunner.dropIndex('app_spec_versions', 'IDX_app_spec_versions_app_version');
    await queryRunner.dropIndex('app_spec_versions', 'IDX_app_spec_versions_tenant_id');
    await queryRunner.dropIndex('app_spec_versions', 'IDX_app_spec_versions_app_id');

    await queryRunner.dropIndex('app_specs', 'IDX_app_specs_tenant_status');
    await queryRunner.dropIndex('app_specs', 'IDX_app_specs_tenant_created');
    await queryRunner.dropIndex('app_specs', 'IDX_app_specs_user_id');
    await queryRunner.dropIndex('app_specs', 'IDX_app_specs_tenant_id');

    // 删除表
    await queryRunner.dropTable('app_spec_versions');
    await queryRunner.dropTable('app_specs');
  }
}
