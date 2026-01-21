package com.ingenio.backend.codegen.schema;

/**
 * 实体关系定义
 */
public class EntityRelationship {

    /**
     * 源实体（表名）
     */
    private String fromEntity;

    /**
     * 目标实体（表名）
     */
    private String toEntity;

    /**
     * 关系类型
     */
    private RelationType type;

    /**
     * 外键字段名
     */
    private String foreignKeyField;

    /**
     * 中间表名称（仅用于MANY_TO_MANY关系）
     */
    private String junctionTable;

    /**
     * 中间表中指向目标实体的外键字段（仅用于MANY_TO_MANY关系）
     */
    private String targetForeignKeyField;

    // Full Constructor
    public EntityRelationship(String fromEntity, String toEntity, RelationType type, String foreignKeyField,
            String junctionTable, String targetForeignKeyField) {
        this.fromEntity = fromEntity;
        this.toEntity = toEntity;
        this.type = type;
        this.foreignKeyField = foreignKeyField;
        this.junctionTable = junctionTable;
        this.targetForeignKeyField = targetForeignKeyField;
    }

    public static EntityRelationshipBuilder builder() {
        return new EntityRelationshipBuilder();
    }

    public static class EntityRelationshipBuilder {
        private String fromEntity;
        private String toEntity;
        private RelationType type;
        private String foreignKeyField;
        private String junctionTable;
        private String targetForeignKeyField;

        EntityRelationshipBuilder() {
        }

        public EntityRelationshipBuilder fromEntity(String fromEntity) {
            this.fromEntity = fromEntity;
            return this;
        }

        public EntityRelationshipBuilder toEntity(String toEntity) {
            this.toEntity = toEntity;
            return this;
        }

        public EntityRelationshipBuilder type(RelationType type) {
            this.type = type;
            return this;
        }

        public EntityRelationshipBuilder foreignKeyField(String foreignKeyField) {
            this.foreignKeyField = foreignKeyField;
            return this;
        }

        public EntityRelationshipBuilder junctionTable(String junctionTable) {
            this.junctionTable = junctionTable;
            return this;
        }

        public EntityRelationshipBuilder targetForeignKeyField(String targetForeignKeyField) {
            this.targetForeignKeyField = targetForeignKeyField;
            return this;
        }

        public EntityRelationship build() {
            return new EntityRelationship(fromEntity, toEntity, type, foreignKeyField, junctionTable,
                    targetForeignKeyField);
        }
    }

    // Getters
    public String getFromEntity() {
        return fromEntity;
    }

    public String getToEntity() {
        return toEntity;
    }

    public RelationType getType() {
        return type;
    }

    public String getForeignKeyField() {
        return foreignKeyField;
    }

    public String getJunctionTable() {
        return junctionTable;
    }

    public String getTargetForeignKeyField() {
        return targetForeignKeyField;
    }

    public enum RelationType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_MANY;

        public boolean isOneToOne() {
            return this == ONE_TO_ONE;
        }

        public boolean isOneToMany() {
            return this == ONE_TO_MANY;
        }

        public boolean isManyToMany() {
            return this == MANY_TO_MANY;
        }
    }

    public boolean isOneToOne() {
        return type == RelationType.ONE_TO_ONE;
    }

    public boolean isOneToMany() {
        return type == RelationType.ONE_TO_MANY;
    }

    public boolean isManyToMany() {
        return type == RelationType.MANY_TO_MANY;
    }

    public String toForeignKeyConstraintSQL(String onDelete) {
        if (isManyToMany()) {
            return String.format(
                    "FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s,\n" +
                            "  FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s",
                    foreignKeyField, fromEntity, onDelete,
                    targetForeignKeyField, toEntity, onDelete);
        } else {
            return String.format(
                    "FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s",
                    foreignKeyField, fromEntity, onDelete != null ? onDelete : "RESTRICT");
        }
    }

    public String generateJunctionTableSQL() {
        if (!isManyToMany()) {
            throw new UnsupportedOperationException("仅多对多关系需要生成中间表");
        }

        if (junctionTable == null || junctionTable.isEmpty()) {
            junctionTable = fromEntity + "_" + toEntity;
        }

        if (targetForeignKeyField == null || targetForeignKeyField.isEmpty()) {
            String singularToEntity = toEntity.endsWith("s") ? toEntity.substring(0, toEntity.length() - 1) : toEntity;
            targetForeignKeyField = singularToEntity + "_id";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- ==========================================\n");
        sql.append("-- Junction Table: ").append(junctionTable).append("\n");
        sql.append("-- ==========================================\n");
        sql.append("CREATE TABLE ").append(junctionTable).append(" (\n");
        sql.append("  ").append(foreignKeyField).append(" UUID NOT NULL REFERENCES ").append(fromEntity)
                .append("(id) ON DELETE CASCADE,\n");
        sql.append("  ").append(targetForeignKeyField).append(" UUID NOT NULL REFERENCES ").append(toEntity)
                .append("(id) ON DELETE CASCADE,\n");
        sql.append("  created_at TIMESTAMPTZ DEFAULT NOW(),\n");
        sql.append("  PRIMARY KEY (").append(foreignKeyField).append(", ").append(targetForeignKeyField).append(")\n");
        sql.append(");\n\n");
        sql.append("CREATE INDEX ").append(junctionTable).append("_").append(foreignKeyField.replace("_id", ""))
                .append("_idx").append(" ON ").append(junctionTable).append("(").append(foreignKeyField).append(");\n");
        sql.append("CREATE INDEX ").append(junctionTable).append("_").append(targetForeignKeyField.replace("_id", ""))
                .append("_idx").append(" ON ").append(junctionTable).append("(").append(targetForeignKeyField)
                .append(");\n");

        return sql.toString();
    }

    public String getDescription() {
        String typeDesc = switch (type) {
            case ONE_TO_ONE -> "1:1";
            case ONE_TO_MANY -> "1:N";
            case MANY_TO_MANY -> "N:M";
        };

        if (isManyToMany()) {
            return String.format("%s (%s) %s [通过%s]", fromEntity, typeDesc, toEntity,
                    junctionTable != null ? junctionTable : "未命名中间表");
        } else {
            return String.format("%s (%s) %s [外键: %s]", fromEntity, typeDesc, toEntity, foreignKeyField);
        }
    }
}
