package com.ingenio.backend.module.g3.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.common.context.TenantContextHolder;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.service.g3.G3ToolsetService;
import com.ingenio.backend.service.g3.G3ToolsetService.BatchFileReadResult;
import com.ingenio.backend.service.g3.G3ToolsetService.FileReadResult;
import com.ingenio.backend.service.g3.G3ToolsetService.SummaryResult;
import com.ingenio.backend.service.g3.G3ToolsetService.SearchResult;
import com.ingenio.backend.service.g3.G3ToolsetService.ToolCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * G3 Toolset API（受控 Shell/文件读取/搜索）。
 */
@RestController
@RequestMapping("/v1/g3/tools")
@Tag(name = "G3 Toolset API", description = "G3 受控执行与文件探索接口")
public class G3ToolsetController {

    private final G3ToolsetService toolsetService;
    private final G3JobMapper jobMapper;

    public G3ToolsetController(G3ToolsetService toolsetService, G3JobMapper jobMapper) {
        this.toolsetService = toolsetService;
        this.jobMapper = jobMapper;
    }

    /**
     * 执行沙箱命令（只读）。
     */
    @PostMapping("/execute")
    @Operation(summary = "执行沙箱命令（只读）")
    public Result<ToolCommandResult> execute(@RequestBody ToolCommandRequest request) {
        UUID jobId = parseUuid(request.getJobId());
        if (jobId == null) {
            return Result.error(400, "jobId 不能为空");
        }

        G3JobEntity job = jobMapper.selectById(jobId);
        if (job == null || !hasJobAccess(job)) {
            return Result.error(403, "无权访问该任务");
        }

        ToolCommandResult result = toolsetService.runSandboxCommand(
                jobId,
                request.getCommand(),
                request.getTimeoutSeconds(),
                entry -> {}
        );
        return Result.success(result);
    }

    /**
     * 读取工作区文件。
     */
    @GetMapping("/read-file")
    @Operation(summary = "读取工作区文件")
    public Result<FileReadResult> readFile(
            @RequestParam("path") String path,
            @RequestParam(value = "maxLines", required = false) Integer maxLines) {
        return Result.success(toolsetService.readWorkspaceFile(path, maxLines));
    }

    /**
     * 工作区文本搜索。
     */
    @GetMapping("/search")
    @Operation(summary = "工作区文本搜索")
    public Result<SearchResult> search(
            @RequestParam("q") String query,
            @RequestParam(value = "maxMatches", required = false) Integer maxMatches) {
        return Result.success(toolsetService.searchWorkspace(query, maxMatches));
    }

    /**
     * 批量读取工作区文件。
     */
    @PostMapping("/read-files")
    @Operation(summary = "批量读取工作区文件")
    public Result<BatchFileReadResult> readFiles(@RequestBody ToolBatchReadRequest request) {
        G3JobEntity job = resolveJobForRequest(request.getJobId());
        if (StringUtils.hasText(request.getJobId()) && job == null) {
            return Result.error(403, "无权访问该任务");
        }
        return Result.success(toolsetService.readWorkspaceFiles(
                request.getPaths(),
                request.getMaxLines(),
                job,
                null
        ));
    }

    /**
     * 批量读取并生成摘要。
     */
    @PostMapping("/summarize")
    @Operation(summary = "批量读取并生成摘要")
    public Result<SummaryResult> summarize(@RequestBody ToolSummarizeRequest request) {
        G3JobEntity job = resolveJobForRequest(request.getJobId());
        if (StringUtils.hasText(request.getJobId()) && job == null) {
            return Result.error(403, "无权访问该任务");
        }
        return Result.success(toolsetService.summarizeWorkspaceFiles(
                request.getPaths(),
                request.getMaxLinesPerFile(),
                request.getMaxSummaryChars(),
                job,
                null
        ));
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private G3JobEntity resolveJobForRequest(String jobId) {
        UUID jobUuid = parseUuid(jobId);
        if (jobUuid == null) {
            return null;
        }
        G3JobEntity job = jobMapper.selectById(jobUuid);
        if (job == null || !hasJobAccess(job)) {
            return null;
        }
        return job;
    }

    private UUID getCurrentUserId() {
        try {
            if (StpUtil.isLogin()) {
                String userIdStr = StpUtil.getLoginIdAsString();
                if (StringUtils.hasText(userIdStr)) {
                    return UUID.fromString(userIdStr);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private UUID getCurrentTenantId() {
        try {
            String tenantIdStr = TenantContextHolder.getTenantId();
            if (StringUtils.hasText(tenantIdStr)) {
                return UUID.fromString(tenantIdStr);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 验证当前用户是否有权访问指定任务。
     */
    private boolean hasJobAccess(G3JobEntity job) {
        UUID currentUserId = getCurrentUserId();
        UUID currentTenantId = getCurrentTenantId();
        UUID jobUserId = job.getUserId();
        UUID jobTenantId = job.getTenantId();

        if (jobUserId == null && jobTenantId == null) {
            return true;
        }
        if (currentUserId != null && currentUserId.equals(jobUserId)) {
            return true;
        }
        return currentTenantId != null && currentTenantId.equals(jobTenantId);
    }

    /**
     * Toolset 命令请求体。
     */
    public static class ToolCommandRequest {
        /**
         * 任务ID。
         */
        private String jobId;
        /**
         * 命令。
         */
        private String command;
        /**
         * 超时（秒）。
         */
        private Integer timeoutSeconds;

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * 批量读取请求体。
     */
    public static class ToolBatchReadRequest {
        /**
         * 可选任务ID。
         */
        private String jobId;
        /**
         * 文件路径列表。
         */
        private java.util.List<String> paths;
        /**
         * 每个文件最大行数。
         */
        private Integer maxLines;

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public java.util.List<String> getPaths() {
            return paths;
        }

        public void setPaths(java.util.List<String> paths) {
            this.paths = paths;
        }

        public Integer getMaxLines() {
            return maxLines;
        }

        public void setMaxLines(Integer maxLines) {
            this.maxLines = maxLines;
        }
    }

    /**
     * 摘要生成请求体。
     */
    public static class ToolSummarizeRequest {
        /**
         * 可选任务ID。
         */
        private String jobId;
        /**
         * 文件路径列表。
         */
        private java.util.List<String> paths;
        /**
         * 每个文件最大行数。
         */
        private Integer maxLinesPerFile;
        /**
         * 摘要最大字符数。
         */
        private Integer maxSummaryChars;

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public java.util.List<String> getPaths() {
            return paths;
        }

        public void setPaths(java.util.List<String> paths) {
            this.paths = paths;
        }

        public Integer getMaxLinesPerFile() {
            return maxLinesPerFile;
        }

        public void setMaxLinesPerFile(Integer maxLinesPerFile) {
            this.maxLinesPerFile = maxLinesPerFile;
        }

        public Integer getMaxSummaryChars() {
            return maxSummaryChars;
        }

        public void setMaxSummaryChars(Integer maxSummaryChars) {
            this.maxSummaryChars = maxSummaryChars;
        }
    }
}
