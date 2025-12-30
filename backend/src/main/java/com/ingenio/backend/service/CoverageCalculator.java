package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.CoverageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 覆盖率计算器（Phase 3）
 *
 * 支持多种覆盖率工具：
 * - Istanbul（JavaScript/TypeScript - Next.js项目）
 * - JaCoCo（Java - Spring Boot项目）
 * - Kover（Kotlin - KMP项目，后续扩展）
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageCalculator {

    private final ObjectMapper objectMapper;

    /**
     * 计算项目覆盖率
     *
     * 根据项目类型自动选择覆盖率工具：
     * - nextjs → Istanbul
     * - spring-boot → JaCoCo
     * - kmp → Kover（未来扩展）
     *
     * @param projectRoot 项目根目录
     * @param projectType 项目类型
     * @return 覆盖率计算结果
     */
    public CoverageResult calculate(String projectRoot, String projectType) {
        log.info("开始计算覆盖率 - projectRoot: {}, projectType: {}", projectRoot, projectType);

        return switch (projectType.toLowerCase()) {
            case "nextjs" -> calculateJavaScriptCoverage(projectRoot);
            case "spring-boot" -> calculateJavaCoverage(projectRoot);
            case "kmp" -> calculateKotlinCoverage(projectRoot);
            default -> {
                log.warn("不支持的项目类型，返回0覆盖率: {}", projectType);
                yield CoverageResult.zero(projectType, "unknown");
            }
        };
    }

    /**
     * 计算JavaScript/TypeScript覆盖率（Istanbul）
     *
     * 解析 coverage/coverage-summary.json 文件：
     * <pre>
     * {
     *   "total": {
     *     "lines": {"total": 100, "covered": 85, "skipped": 0, "pct": 85},
     *     "statements": {"total": 120, "covered": 100, "skipped": 0, "pct": 83.33},
     *     "functions": {"total": 30, "covered": 28, "skipped": 0, "pct": 93.33},
     *     "branches": {"total": 40, "covered": 35, "skipped": 0, "pct": 87.5}
     *   }
     * }
     * </pre>
     *
     * @param projectRoot 项目根目录
     * @return 覆盖率结果
     */
    private CoverageResult calculateJavaScriptCoverage(String projectRoot) {
        log.debug("解析Istanbul覆盖率报告: {}", projectRoot);

        try {
            // Istanbul覆盖率报告路径
            Path coveragePath = Paths.get(projectRoot, "coverage", "coverage-summary.json");

            if (!Files.exists(coveragePath)) {
                log.warn("Istanbul覆盖率报告不存在: {}", coveragePath);
                return CoverageResult.zero("nextjs", "istanbul");
            }

            // 解析JSON报告
            JsonNode root = objectMapper.readTree(coveragePath.toFile());
            JsonNode total = root.get("total");

            if (total == null) {
                log.warn("Istanbul报告格式错误：缺少total字段");
                return CoverageResult.zero("nextjs", "istanbul");
            }

            // 提取覆盖率数据
            double lineCoverage = getPercentage(total, "lines");
            double branchCoverage = getPercentage(total, "branches");
            double functionCoverage = getPercentage(total, "functions");
            double statementCoverage = getPercentage(total, "statements");

            // 计算总体覆盖率（行覆盖率和分支覆盖率的平均值）
            double overallCoverage = (lineCoverage + branchCoverage) / 2.0;

            boolean meetsQualityGate = overallCoverage >= 0.85;

            CoverageResult result = CoverageResult.builder()
                    .projectType("nextjs")
                    .tool("istanbul")
                    .overallCoverage(overallCoverage)
                    .lineCoverage(lineCoverage)
                    .branchCoverage(branchCoverage)
                    .functionCoverage(functionCoverage)
                    .statementCoverage(statementCoverage)
                    .meetsQualityGate(meetsQualityGate)
                    .reportPath(coveragePath.toString())
                    .filesCoverage(new HashMap<>()) // 简化实现：暂不解析文件级别覆盖率
                    .build();

            log.info("Istanbul覆盖率计算完成 - overall: {:.2f}%, line: {:.2f}%, branch: {:.2f}%, meetsQualityGate: {}",
                    overallCoverage * 100, lineCoverage * 100, branchCoverage * 100, meetsQualityGate);

            return result;

        } catch (Exception e) {
            log.error("解析Istanbul覆盖率报告失败", e);
            return CoverageResult.zero("nextjs", "istanbul");
        }
    }

    /**
     * 计算Java覆盖率（JaCoCo）
     *
     * 解析 target/site/jacoco/jacoco.xml 文件：
     * <pre>
     * &lt;report&gt;
     *   &lt;counter type="LINE" missed="15" covered="85"/&gt;
     *   &lt;counter type="BRANCH" missed="5" covered="35"/&gt;
     *   &lt;counter type="METHOD" missed="2" covered="28"/&gt;
     * &lt;/report&gt;
     * </pre>
     *
     * @param projectRoot 项目根目录
     * @return 覆盖率结果
     */
    private CoverageResult calculateJavaCoverage(String projectRoot) {
        log.debug("解析JaCoCo覆盖率报告: {}", projectRoot);

        try {
            // JaCoCo覆盖率报告路径
            Path jacocoPath = Paths.get(projectRoot, "target", "site", "jacoco", "jacoco.xml");

            if (!Files.exists(jacocoPath)) {
                log.warn("JaCoCo覆盖率报告不存在: {}", jacocoPath);
                return CoverageResult.zero("spring-boot", "jacoco");
            }

            // 解析XML报告
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(jacocoPath.toFile());

            // 提取counter节点
            NodeList counters = document.getElementsByTagName("counter");

            double lineCoverage = 0.0;
            double branchCoverage = 0.0;
            double methodCoverage = 0.0;

            for (int i = 0; i < counters.getLength(); i++) {
                Element counter = (Element) counters.item(i);
                String type = counter.getAttribute("type");
                int missed = Integer.parseInt(counter.getAttribute("missed"));
                int covered = Integer.parseInt(counter.getAttribute("covered"));
                int total = missed + covered;

                if (total == 0) continue;

                double coverage = (double) covered / total;

                switch (type) {
                    case "LINE" -> lineCoverage = coverage;
                    case "BRANCH" -> branchCoverage = coverage;
                    case "METHOD" -> methodCoverage = coverage;
                }
            }

            // 计算总体覆盖率（行覆盖率和分支覆盖率的平均值）
            double overallCoverage = (lineCoverage + branchCoverage) / 2.0;

            boolean meetsQualityGate = overallCoverage >= 0.85;

            CoverageResult result = CoverageResult.builder()
                    .projectType("spring-boot")
                    .tool("jacoco")
                    .overallCoverage(overallCoverage)
                    .lineCoverage(lineCoverage)
                    .branchCoverage(branchCoverage)
                    .functionCoverage(methodCoverage)
                    .statementCoverage(null) // JaCoCo不提供语句覆盖率
                    .meetsQualityGate(meetsQualityGate)
                    .reportPath(jacocoPath.toString())
                    .filesCoverage(new HashMap<>()) // 简化实现：暂不解析文件级别覆盖率
                    .build();

            log.info("JaCoCo覆盖率计算完成 - overall: {:.2f}%, line: {:.2f}%, branch: {:.2f}%, meetsQualityGate: {}",
                    overallCoverage * 100, lineCoverage * 100, branchCoverage * 100, meetsQualityGate);

            return result;

        } catch (Exception e) {
            log.error("解析JaCoCo覆盖率报告失败", e);
            return CoverageResult.zero("spring-boot", "jacoco");
        }
    }

    /**
     * 计算Kotlin覆盖率（Kover）
     *
     * 未来扩展
     *
     * @param projectRoot 项目根目录
     * @return 覆盖率结果
     */
    private CoverageResult calculateKotlinCoverage(String projectRoot) {
        log.warn("Kotlin覆盖率计算尚未实现，返回0覆盖率");
        return CoverageResult.zero("kmp", "kover");
    }

    /**
     * 从JSON节点提取百分比
     *
     * @param parent 父节点
     * @param key 键名（lines/branches/functions/statements）
     * @return 百分比（0.0 - 1.0）
     */
    private double getPercentage(JsonNode parent, String key) {
        JsonNode node = parent.get(key);
        if (node == null) return 0.0;

        JsonNode pct = node.get("pct");
        if (pct == null) return 0.0;

        return pct.asDouble() / 100.0; // 转换为0.0-1.0
    }
}
