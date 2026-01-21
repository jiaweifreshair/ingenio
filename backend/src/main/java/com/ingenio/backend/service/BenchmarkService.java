package com.ingenio.backend.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基准页面（Benchmarks）服务
 *
 * 用途：
 * - 将“挑战赛标杆 HTML”（如 /Users/.../Ingenio_Benchmarks_CN_HTML）作为产品的最小验收基准；
 * - 提供后端 API 给前端加载（列表/原文），用于对照 UI 生成能力与链路验证。
 *
 * 设计原则：
 * - 默认优先读取文件系统目录（便于本地快速迭代，且不需要把大文件入库）；
 * - 若未配置且默认目录不存在，则回退到 classpath:benchmarks/（可选，便于 CI/容器环境）。
 */
@Slf4j
@Service
public class BenchmarkService {

    private static final List<String> DEFAULT_IDS = List.of("index", "primary", "middle", "high", "vocational");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);

    private final ResourceLoader resourceLoader;

    /**
     * 可选：外部基准目录（推荐）。
     *
     * <p>示例：</p>
     * <ul>
     *   <li>ingenio.benchmarks.dir=/Users/apus/Desktop/MBA课程/Ingenio_Benchmarks_CN_HTML</li>
     * </ul>
     */
    @Value("${ingenio.benchmarks.dir:}")
    private String benchmarksDir;

    public BenchmarkService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 列出可用基准页面（按 id 排序）
     */
    public List<BenchmarkSummary> listBenchmarks() {
        List<BenchmarkSummary> result = new ArrayList<>();

        for (String id : DEFAULT_IDS) {
            Optional<String> html = readBenchmarkHtmlOptional(id);
            if (html.isEmpty()) continue;

            String title = extractTitle(html.get()).orElse(id);
            result.add(BenchmarkSummary.builder()
                    .id(id)
                    .title(title)
                    .source(resolveSource())
                    .build());
        }

        result.sort(Comparator.comparing(BenchmarkSummary::getId));
        return result;
    }

    /**
     * 获取基准 HTML 原文（找不到则抛异常）
     */
    public String getBenchmarkHtml(String id) {
        validateId(id);
        return readBenchmarkHtmlOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("基准页面不存在: " + id));
    }

    private Optional<String> readBenchmarkHtmlOptional(String id) {
        validateId(id);

        // 1) 文件系统优先（便于本地迭代）
        Path dir = resolveBenchmarksDir();
        if (dir != null) {
            Path file = dir.resolve(id + ".html").normalize();
            if (file.startsWith(dir) && Files.isRegularFile(file)) {
                try {
                    return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.warn("[Benchmarks] 读取文件失败: file={}, err={}", file, e.getMessage());
                }
            }
        }

        // 2) classpath 兜底（可选）
        String location = "classpath:benchmarks/" + id + ".html";
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            try (var in = resource.getInputStream()) {
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("[Benchmarks] 读取classpath资源失败: location={}, err={}", location, e.getMessage());
            }
        }

        return Optional.empty();
    }

    private Path resolveBenchmarksDir() {
        String configured = benchmarksDir != null ? benchmarksDir.trim() : "";
        if (!configured.isEmpty()) {
            Path dir = Paths.get(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(dir)) return dir;
            return null;
        }

        // 默认尝试：用户桌面目录（本地迭代高频）
        Path defaultDir = Paths.get(
                System.getProperty("user.home", ""),
                "Desktop",
                "MBA课程",
                "Ingenio_Benchmarks_CN_HTML"
        ).toAbsolutePath().normalize();

        return Files.isDirectory(defaultDir) ? defaultDir : null;
    }

    private String resolveSource() {
        Path dir = resolveBenchmarksDir();
        return dir != null ? "filesystem" : "classpath";
    }

    private Optional<String> extractTitle(String html) {
        if (html == null || html.isBlank()) return Optional.empty();
        Matcher m = TITLE_PATTERN.matcher(html);
        if (!m.find()) return Optional.empty();
        String title = m.group(1);
        if (title == null) return Optional.empty();
        // 简单清洗空白
        title = title.replaceAll("\\s+", " ").trim();
        return title.isEmpty() ? Optional.empty() : Optional.of(title);
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id不能为空");
        }
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("非法id");
        }
    }

    /**
     * 基准页面摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkSummary {
        /**
         * 逻辑ID：index/primary/middle/high/vocational
         */
        private String id;

        /**
         * HTML title（用于展示）
         */
        private String title;

        /**
         * 来源：filesystem / classpath
         */
        private String source;
    }
}

