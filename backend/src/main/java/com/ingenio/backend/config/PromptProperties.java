package com.ingenio.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 提示词（Prompt）配置。
 *
 * <p>
 * 设计目标：
 * </p>
 * <ul>
 * <li>将提示词从 Java 代码中剥离，避免“硬编码 + 难维护 + 难热更新”；</li>
 * <li>支持通过 classpath 或文件系统路径加载，便于在不同环境做差异化配置；</li>
 * <li>为 G3 引擎（Architect/Coder/Coach）提供统一的提示词入口，减少散落在各个 Agent 中的重复模板。</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ingenio.prompts")
public class PromptProperties {

    /**
     * ArchitectAgent 相关提示词配置。
     */
    private Architect architect = new Architect();

    /**
     * BackendCoderAgent 相关提示词配置。
     */
    private Coder coder = new Coder();

    /**
     * CoachAgent 相关提示词配置。
     */
    private Coach coach = new Coach();

    /**
     * GEO（生成引擎优化）相关提示词配置。
     *
     * 是什么：GEO 工作台的提示词资产配置入口。
     * 做什么：为 E-E-A-T 审校、关键词挖掘等能力提供可外置可迭代的 Prompt。
     * 为什么：提示词属于运行资产，应避免硬编码在 Java 代码中，便于灰度与热更新。
     */
    private Geo geo = new Geo();

    public Architect getArchitect() {
        return architect;
    }

    public void setArchitect(Architect architect) {
        this.architect = architect;
    }

    public Coder getCoder() {
        return coder;
    }

    public void setCoder(Coder coder) {
        this.coder = coder;
    }

    public Coach getCoach() {
        return coach;
    }

    public void setCoach(Coach coach) {
        this.coach = coach;
    }

    public Geo getGeo() {
        return geo;
    }

    public void setGeo(Geo geo) {
        this.geo = geo;
    }

    @Data
    public static class Architect {
        /**
         * OpenAPI 契约生成提示词模板路径。
         */
        private String contract = "classpath:prompts/architect/contract.txt";

        /**
         * 数据库 Schema（DDL）生成提示词模板路径。
         */
        private String schema = "classpath:prompts/architect/schema.txt";

        public String getContract() {
            return contract;
        }

        public void setContract(String contract) {
            this.contract = contract;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }

    @Data
    public static class Coder {
        /**
         * 全局代码规范提示词模板路径（会拼接到各类生成提示词的开头）。
         */
        private String standards = "classpath:prompts/coder/code-standards.txt";

        /**
         * Entity 生成提示词模板路径。
         */
        private String entity = "classpath:prompts/coder/entity.txt";

        /**
         * Mapper 生成提示词模板路径。
         */
        private String mapper = "classpath:prompts/coder/mapper.txt";

        /**
         * DTO 生成提示词模板路径。
         */
        private String dto = "classpath:prompts/coder/dto.txt";

        /**
         * Service 生成提示词模板路径。
         */
        private String service = "classpath:prompts/coder/service.txt";

        /**
         * Controller 生成提示词模板路径。
         */
        private String controller = "classpath:prompts/coder/controller.txt";

        public String getStandards() {
            return standards;
        }

        public void setStandards(String standards) {
            this.standards = standards;
        }

        public String getEntity() {
            return entity;
        }

        public void setEntity(String entity) {
            this.entity = entity;
        }

        public String getMapper() {
            return mapper;
        }

        public void setMapper(String mapper) {
            this.mapper = mapper;
        }

        public String getDto() {
            return dto;
        }

        public void setDto(String dto) {
            this.dto = dto;
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getController() {
            return controller;
        }

        public void setController(String controller) {
            this.controller = controller;
        }
    }

    @Data
    public static class Coach {
        /**
         * 错误分析提示词模板路径。
         */
        private String analysis = "classpath:prompts/coach/analysis.txt";

        /**
         * 修复计划提示词模板路径。
         *
         * 是什么：指向 Coach 生成修复计划的提示词模板文件。
         * 做什么：为修复前的计划阶段提供可配置提示词入口。
         * 为什么：避免硬编码提示词，便于在不同环境迭代优化。
         */
        private String plan = "classpath:prompts/coach/plan.txt";

        /**
         * Java 代码修复提示词模板路径。
         */
        private String fix = "classpath:prompts/coach/fix.txt";

        /**
         * pom.xml 修复提示词模板路径。
         */
        private String fixPomXml = "classpath:prompts/coach/fix-pom-xml.txt";

        public String getAnalysis() {
            return analysis;
        }

        public void setAnalysis(String analysis) {
            this.analysis = analysis;
        }

        /**
         * 获取修复计划提示词模板路径。
         *
         * 是什么：读取 Coach 修复计划模板的配置值。
         * 做什么：为提示词加载服务提供路径。
         * 为什么：保持提示词可配置、可热更新的能力。
         */
        public String getPlan() {
            return plan;
        }

        /**
         * 设置修复计划提示词模板路径。
         *
         * 是什么：写入 Coach 修复计划模板路径。
         * 做什么：支持按环境覆盖默认提示词文件。
         * 为什么：便于运行时切换或灰度优化提示词。
         */
        public void setPlan(String plan) {
            this.plan = plan;
        }

        public String getFix() {
            return fix;
        }

        public void setFix(String fix) {
            this.fix = fix;
        }

        public String getFixPomXml() {
            return fixPomXml;
        }

        public void setFixPomXml(String fixPomXml) {
            this.fixPomXml = fixPomXml;
        }
    }

    @Data
    public static class Geo {

        /**
         * GEO E-E-A-T 审校提示词模板路径。
         */
        private String review = "classpath:prompts/geo/review.txt";

        /**
         * GEO 关键词挖掘提示词模板路径。
         */
        private String keywords = "classpath:prompts/geo/keywords.txt";

        public String getReview() {
            return review;
        }

        public void setReview(String review) {
            this.review = review;
        }

        public String getKeywords() {
            return keywords;
        }

        public void setKeywords(String keywords) {
            this.keywords = keywords;
        }
    }
}
