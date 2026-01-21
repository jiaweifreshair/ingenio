package com.ingenio.backend.dto.blueprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintSpec {
    private String id;
    private String name;
    private String description;
    private String category;
    private String subcategory;
    private List<String> keywords;
    private String complexity;
    private Constraints constraints;
    private List<SchemaTable> schema;
    private List<String> features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Constraints {
        private String techStack;
        private String database;
        private String auth;
        private String apiStyle;

        public String getTechStack() {
            return techStack;
        }

        public String getDatabase() {
            return database;
        }

        public String getAuth() {
            return auth;
        }

        public String getApiStyle() {
            return apiStyle;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchemaTable {
        private String tableName;
        private String comment;
        private List<SchemaColumn> columns;

        public String getTableName() {
            return tableName;
        }

        public String getComment() {
            return comment;
        }

        public List<SchemaColumn> getColumns() {
            return columns;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchemaColumn {
        private String name;
        private String type;
        private String constraints;
        private String comment;
        private List<String> columns; // For composite keys

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getConstraints() {
            return constraints;
        }

        public String getComment() {
            return comment;
        }

        public List<String> getColumns() {
            return columns;
        }
    }

    // Manual getters for BlueprintSpec
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String getComplexity() {
        return complexity;
    }

    public Constraints getConstraints() {
        return constraints;
    }

    public List<SchemaTable> getSchema() {
        return schema;
    }

    public List<String> getFeatures() {
        return features;
    }
}
