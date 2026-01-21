package com.ingenio.backend.codegen.ai.model;

/**
 * 业务规则定义（V2.0 Phase 4.1）
 */
public class BusinessRule {

    private String name;
    private String description;
    private BusinessRuleType type;
    private String entity;
    private String method;
    private String logic;
    private Integer priority;

    public BusinessRule() {
    }

    public BusinessRule(String name, String description, BusinessRuleType type, String entity, String method,
            String logic, Integer priority) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.entity = entity;
        this.method = method;
        this.logic = logic;
        this.priority = priority;
    }

    public static BusinessRuleBuilder builder() {
        return new BusinessRuleBuilder();
    }

    public static class BusinessRuleBuilder {
        private String name;
        private String description;
        private BusinessRuleType type;
        private String entity;
        private String method;
        private String logic;
        private Integer priority;

        public BusinessRuleBuilder name(String name) {
            this.name = name;
            return this;
        }

        public BusinessRuleBuilder description(String description) {
            this.description = description;
            return this;
        }

        public BusinessRuleBuilder type(BusinessRuleType type) {
            this.type = type;
            return this;
        }

        public BusinessRuleBuilder entity(String entity) {
            this.entity = entity;
            return this;
        }

        public BusinessRuleBuilder method(String method) {
            this.method = method;
            return this;
        }

        public BusinessRuleBuilder logic(String logic) {
            this.logic = logic;
            return this;
        }

        public BusinessRuleBuilder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public BusinessRule build() {
            return new BusinessRule(name, description, type, entity, method, logic, priority);
        }
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BusinessRuleType getType() {
        return type;
    }

    public void setType(BusinessRuleType type) {
        this.type = type;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getLogic() {
        return logic;
    }

    public void setLogic(String logic) {
        this.logic = logic;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public enum BusinessRuleType {
        VALIDATION,
        CALCULATION,
        WORKFLOW,
        NOTIFICATION
    }
}
