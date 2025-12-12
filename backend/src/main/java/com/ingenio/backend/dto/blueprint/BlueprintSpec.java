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
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchemaTable {
        private String tableName;
        private String comment;
        private List<SchemaColumn> columns;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchemaColumn {
        private String name;
        private String type;
        private String constraints;
        private String comment;
        private List<String> columns; // For composite keys
    }
}
