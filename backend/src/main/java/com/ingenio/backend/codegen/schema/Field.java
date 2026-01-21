package com.ingenio.backend.codegen.schema;

/**
 * 数据库字段定义
 */
public class Field {

    private String name;
    private FieldType type;
    private Integer length;
    private Integer precision;
    private Integer scale;
    private boolean primaryKey;
    private boolean unique;
    private boolean nullable;
    private String defaultValue;
    private String checkConstraint;
    private String foreignKey;
    private String onDelete;
    private String description;
    private boolean indexed;

    // Full Constructor
    public Field(String name, FieldType type, Integer length, Integer precision, Integer scale, boolean primaryKey,
            boolean unique, boolean nullable, String defaultValue, String checkConstraint, String foreignKey,
            String onDelete, String description, boolean indexed) {
        this.name = name;
        this.type = type;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.primaryKey = primaryKey;
        this.unique = unique;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.checkConstraint = checkConstraint;
        this.foreignKey = foreignKey;
        this.onDelete = onDelete != null ? onDelete : "RESTRICT";
        this.description = description;
        this.indexed = indexed;
    }

    public static FieldBuilder builder() {
        return new FieldBuilder();
    }

    public static class FieldBuilder {
        private String name;
        private FieldType type;
        private Integer length;
        private Integer precision;
        private Integer scale;
        private boolean primaryKey = false;
        private boolean unique = false;
        private boolean nullable = true;
        private String defaultValue;
        private String checkConstraint;
        private String foreignKey;
        private String onDelete = "RESTRICT";
        private String description;
        private boolean indexed = false;

        FieldBuilder() {
        }

        public FieldBuilder name(String name) {
            this.name = name;
            return this;
        }

        public FieldBuilder type(FieldType type) {
            this.type = type;
            return this;
        }

        public FieldBuilder length(Integer length) {
            this.length = length;
            return this;
        }

        public FieldBuilder precision(Integer precision) {
            this.precision = precision;
            return this;
        }

        public FieldBuilder scale(Integer scale) {
            this.scale = scale;
            return this;
        }

        public FieldBuilder primaryKey(boolean primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public FieldBuilder unique(boolean unique) {
            this.unique = unique;
            return this;
        }

        public FieldBuilder nullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public FieldBuilder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FieldBuilder checkConstraint(String checkConstraint) {
            this.checkConstraint = checkConstraint;
            return this;
        }

        public FieldBuilder foreignKey(String foreignKey) {
            this.foreignKey = foreignKey;
            return this;
        }

        public FieldBuilder onDelete(String onDelete) {
            this.onDelete = onDelete;
            return this;
        }

        public FieldBuilder description(String description) {
            this.description = description;
            return this;
        }

        public FieldBuilder indexed(boolean indexed) {
            this.indexed = indexed;
            return this;
        }

        public Field build() {
            return new Field(name, type, length, precision, scale, primaryKey, unique, nullable, defaultValue,
                    checkConstraint, foreignKey, onDelete, description, indexed);
        }
    }

    // Getters
    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public Integer getLength() {
        return length;
    }

    public Integer getPrecision() {
        return precision;
    }

    public Integer getScale() {
        return scale;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isUnique() {
        return unique;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getCheckConstraint() {
        return checkConstraint;
    }

    public String getForeignKey() {
        return foreignKey;
    }

    public String getOnDelete() {
        return onDelete;
    }

    public String getDescription() {
        return description;
    }

    public boolean isIndexed() {
        return indexed;
    }
}
