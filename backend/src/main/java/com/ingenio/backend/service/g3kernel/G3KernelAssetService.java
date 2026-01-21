package com.ingenio.backend.service.g3kernel;

import com.ingenio.backend.module.g3kernel.controller.G3KernelController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * G3 Kernel 资产读取与缓存服务。
 *
 * <p>为什么需要该服务：</p>
 * <ul>
 *   <li>模板/提示词属于“运行资产”，应从后端统一输出，避免前端/Agent 侧硬编码导致版本漂移；</li>
 *   <li>文件读取需要做安全约束（禁止路径穿越），并提供轻量缓存降低 IO。</li>
 * </ul>
 */
@Slf4j
@Service
public class G3KernelAssetService {

    /**
     * Kernel 上下文目录，默认指向 backend 工程根下的 `g3_context`。
     *
     * <p>注意：该目录为运行资产，默认随仓库存在；若部署时需外置，可通过配置覆盖。</p>
     */
    @Value("${ingenio.g3.kernel.context-dir:g3_context}")
    private String contextDir;

    private final Map<String, CachedAsset> cache = new ConcurrentHashMap<>();

    public G3KernelController.KernelIndexResponse getIndex() {
        Path templatesDir = resolveSafeDir("templates");
        Path promptsDir = resolveSafeDir("prompts");

        List<String> templates = listFileNames(templatesDir);
        List<String> prompts = listFileNames(promptsDir);

        return new G3KernelController.KernelIndexResponse(templates, prompts, "capabilities.md");
    }

    public String getTemplateContent(String name) {
        return readAsset("templates", name);
    }

    public String getPromptContent(String name) {
        return readAsset("prompts", name);
    }

    public String getCapabilitiesContent() {
        return readAsset(null, "capabilities.md");
    }

    private List<String> listFileNames(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("[G3Kernel] 列目录失败: dir={}, error={}", dir, e.getMessage());
            return List.of();
        }
    }

    private Path resolveSafeDir(String subDir) {
        Path base = Paths.get(contextDir).toAbsolutePath().normalize();
        Path dir = base.resolve(subDir).normalize();
        if (!dir.startsWith(base)) {
            throw new IllegalArgumentException("非法目录访问");
        }
        return dir;
    }

    private String readAsset(String subDir, String name) {
        validateAssetName(name);

        Path base = Paths.get(contextDir).toAbsolutePath().normalize();
        Path target = subDir == null ? base.resolve(name) : base.resolve(subDir).resolve(name);
        target = target.normalize();

        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法文件访问");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("资产不存在: " + name);
        }

        CachedAsset cached = cache.get(target.toString());
        try {
            long mtime = Files.getLastModifiedTime(target).toMillis();
            if (cached != null && cached.lastModifiedMs == mtime) {
                return cached.content;
            }

            String content = Files.readString(target, StandardCharsets.UTF_8);
            cache.put(target.toString(), new CachedAsset(content, mtime));
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("读取资产失败: " + name + "，原因: " + e.getMessage(), e);
        }
    }

    /**
     * 资产名白名单校验：仅允许文件名，不允许路径分隔符/路径穿越。
     */
    private void validateAssetName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("资产名不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("非法资产名");
        }
        // 允许常见文件名字符：字母数字/点/下划线/中划线
        if (!name.matches("^[A-Za-z0-9._-]+$")) {
            throw new IllegalArgumentException("非法资产名");
        }
    }

    private record CachedAsset(String content, long lastModifiedMs) {}
}

