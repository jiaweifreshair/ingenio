package com.ingenio.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * G3 Toolset 配置（受控 Shell/文件读取/搜索）。
 */
@Configuration
@ConfigurationProperties(prefix = "ingenio.g3.tools")
public class G3ToolsetProperties {

    /**
     * 是否启用工具集能力（对外 API 与内部调用）。
     */
    private boolean enabled = true;

    /**
     * 工作区根目录（用于 read_file/search_codebase 的安全边界）。
     */
    private String workspaceRoot;

    /**
     * 允许的命令（L0 只读）。
     */
    private List<String> allowCommands = new ArrayList<>(
            List.of("ls", "pwd", "cat", "tail", "head", "rg", "find", "sed", "wc", "stat"));

    /**
     * 禁止执行的命令（用于二次兜底）。
     */
    private List<String> denyCommands = new ArrayList<>(
            List.of("rm", "mv", "cp", "chmod", "chown", "kill", "pkill", "shutdown", "reboot"));

    /**
     * 允许读取/搜索的文件扩展名（减少泄露风险）。
     */
    private List<String> allowFileExtensions = new ArrayList<>(
            List.of(".java", ".xml", ".yml", ".yaml", ".properties", ".md", ".sql", ".ts", ".tsx", ".js", ".json"));

    /**
     * 搜索/读取时跳过的路径片段（目录级黑名单）。
     */
    private List<String> excludePathContains = new ArrayList<>(
            List.of("/node_modules/", "/.git/", "/target/", "/dist/", "/build/", "/.next/", "/.idea/", "/.gradle/"));

    /**
     * 单文件读取最大字节数（默认 512KB）。
     */
    private long maxFileSizeBytes = 512 * 1024;

    /**
     * 搜索时允许扫描的单文件最大字节数（默认 256KB）。
     */
    private long maxSearchFileSizeBytes = 256 * 1024;

    /**
     * 搜索时最多扫描文件数（用于避免极端大仓库阻塞）。
     */
    private int maxSearchFiles = 5000;

    /**
     * 批量读取最大文件数。
     */
    private int maxBatchFiles = 20;

    /**
     * 最大输出行数。
     */
    private int maxOutputLines = 200;

    /**
     * 最大输出字符数。
     */
    private int maxOutputChars = 20_000;

    /**
     * 默认命令超时（秒）。
     */
    private int defaultTimeoutSeconds = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public List<String> getAllowCommands() {
        return allowCommands;
    }

    public void setAllowCommands(List<String> allowCommands) {
        this.allowCommands = allowCommands;
    }

    public List<String> getDenyCommands() {
        return denyCommands;
    }

    public void setDenyCommands(List<String> denyCommands) {
        this.denyCommands = denyCommands;
    }

    public List<String> getAllowFileExtensions() {
        return allowFileExtensions;
    }

    public void setAllowFileExtensions(List<String> allowFileExtensions) {
        this.allowFileExtensions = allowFileExtensions;
    }

    public List<String> getExcludePathContains() {
        return excludePathContains;
    }

    public void setExcludePathContains(List<String> excludePathContains) {
        this.excludePathContains = excludePathContains;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public long getMaxSearchFileSizeBytes() {
        return maxSearchFileSizeBytes;
    }

    public void setMaxSearchFileSizeBytes(long maxSearchFileSizeBytes) {
        this.maxSearchFileSizeBytes = maxSearchFileSizeBytes;
    }

    public int getMaxSearchFiles() {
        return maxSearchFiles;
    }

    public void setMaxSearchFiles(int maxSearchFiles) {
        this.maxSearchFiles = maxSearchFiles;
    }

    public int getMaxBatchFiles() {
        return maxBatchFiles;
    }

    public void setMaxBatchFiles(int maxBatchFiles) {
        this.maxBatchFiles = maxBatchFiles;
    }

    public int getMaxOutputLines() {
        return maxOutputLines;
    }

    public void setMaxOutputLines(int maxOutputLines) {
        this.maxOutputLines = maxOutputLines;
    }

    public int getMaxOutputChars() {
        return maxOutputChars;
    }

    public void setMaxOutputChars(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }
}
