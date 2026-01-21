package com.ingenio.backend.codegen.schema;

import java.util.List;
import java.util.ArrayList;

/**
 * 数据库实体定义
 */
public class Entity {

    /**
     * 表名（PostgreSQL规范：小写+下划线）
     */
    private String name;

    /**
     * 表描述（用于生成注释）
     */
    private String description;

    /**
     * 字段列表
     */
    private List<Field> fields;

    /**
     * 索引列表
     */
    private List<Index> indexes;

    /**
     * 是否启用RLS
     */
    private boolean rlsEnabled;

    /**
     * RLS策略列表
     */
    private List<RLSPolicy> rlsPolicies;

    /**
     * 是否启用软删除
     */
    private boolean softDelete;

    /**
     * 是否启用时间戳
     */
    private boolean timestamps;

    // Default Constructor
    public Entity() {
        this.indexes = new ArrayList<>();
        this.rlsPolicies = new ArrayList<>();
        this.rlsEnabled = true;
        this.softDelete = false;
        this.timestamps = true;
    }

    // AllArgs Constructor
    public Entity(String name, String description, List<Field> fields, List<Index> indexes, boolean rlsEnabled,
            List<RLSPolicy> rlsPolicies, boolean softDelete, boolean timestamps) {
        this.name = name;
        this.description = description;
        this.fields = fields;
        this.indexes = indexes != null ? indexes : new ArrayList<>();
        this.rlsEnabled = rlsEnabled;
        this.rlsPolicies = rlsPolicies != null ? rlsPolicies : new ArrayList<>();
        this.softDelete = softDelete;
        this.timestamps = timestamps;
    }

    public static EntityBuilder builder() {
        return new EntityBuilder();
    }

    public static class EntityBuilder {
        private String name;
        private String description;
        private List<Field> fields;
        private List<Index> indexes = new ArrayList<>();
        private boolean rlsEnabled = true;
        private List<RLSPolicy> rlsPolicies = new ArrayList<>();
        private boolean softDelete = false;
        private boolean timestamps = true;

        EntityBuilder() {
        }

        public EntityBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EntityBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EntityBuilder fields(List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public EntityBuilder indexes(List<Index> indexes) {
            this.indexes = indexes;
            return this;
        }

        public EntityBuilder rlsEnabled(boolean rlsEnabled) {
            this.rlsEnabled = rlsEnabled;
            return this;
        }

        public EntityBuilder rlsPolicies(List<RLSPolicy> rlsPolicies) {
            this.rlsPolicies = rlsPolicies;
            return this;
        }

        public EntityBuilder softDelete(boolean softDelete) {
            this.softDelete = softDelete;
            return this;
        }

        public EntityBuilder timestamps(boolean timestamps) {
            this.timestamps = timestamps;
            return this;
        }

        public Entity build() {
            return new Entity(name, description, fields, indexes, rlsEnabled, rlsPolicies, softDelete, timestamps);
        }
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Field> getFields() {
        return fields;
    }

    public List<Index> getIndexes() {
        return indexes;
    }

    public boolean isRlsEnabled() {
        return rlsEnabled;
    }

    public List<RLSPolicy> getRlsPolicies() {
        return rlsPolicies;
    }

    public boolean isSoftDelete() {
        return softDelete;
    }

    public boolean isTimestamps() {
        return timestamps;
    }

    // Setters
    public void setRlsPolicies(List<RLSPolicy> rlsPolicies) {
        this.rlsPolicies = rlsPolicies;
    }
}
