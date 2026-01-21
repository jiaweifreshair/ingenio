package com.ingenio.backend.module.g3.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.common.context.TenantContextHolder;
import com.ingenio.backend.service.g3.G3KnowledgeStorePort;
import com.ingenio.backend.service.g3.G3RepoIndexService;
import com.ingenio.backend.service.g3.G3RepoIndexService.RepoIndexRequest;
import com.ingenio.backend.service.g3.G3RepoIndexService.RepoIndexResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * G3 知识库 API（Repo RAG）。
 */
@RestController
@RequestMapping("/v1/g3/knowledge")
@Tag(name = "G3 Knowledge API", description = "G3 RAG 知识库索引与检索接口")
public class G3KnowledgeController {

    private final G3RepoIndexService repoIndexService;
    private final G3KnowledgeStorePort knowledgeStore;

    public G3KnowledgeController(G3RepoIndexService repoIndexService, G3KnowledgeStorePort knowledgeStore) {
        this.repoIndexService = repoIndexService;
        this.knowledgeStore = knowledgeStore;
    }

    /**
     * 触发仓库索引。
     */
    @PostMapping("/repo/index")
    @Operation(summary = "触发仓库索引")
    public Result<RepoIndexResult> indexRepo(@RequestBody RepoIndexRequest request) {
        String tenantId = request.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            tenantId = TenantContextHolder.getTenantId();
            request.setTenantId(tenantId);
        }

        RepoIndexResult result = repoIndexService.indexRepo(request, entry -> {});
        return Result.success(result);
    }

    /**
     * 仓库语义检索。
     */
    @GetMapping("/repo/search")
    @Operation(summary = "仓库语义检索")
    public Result<List<RepoSearchHit>> searchRepo(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", required = false) Integer topK,
            @RequestParam(value = "projectId", required = false) String projectId) {

        UUID tenant = parseUuid(TenantContextHolder.getTenantId());
        UUID project = parseUuid(projectId);
        int limit = topK != null ? topK : 5;

        List<?> docs = knowledgeStore.searchRepo(query, tenant, project, limit);
        List<RepoSearchHit> hits = docs.stream()
                .map(doc -> {
                    if (doc instanceof org.springframework.ai.document.Document springDoc) {
                        String filePath = String.valueOf(
                                springDoc.getMetadata().getOrDefault("filePath",
                                        springDoc.getMetadata().get("fileName")));
                        return new RepoSearchHit(filePath, springDoc.getScore(), springDoc.getContent());
                    }
                    return new RepoSearchHit("unknown", null, String.valueOf(doc));
                })
                .collect(Collectors.toList());

        return Result.success(hits);
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

    /**
     * 仓库检索命中。
     */
    public static class RepoSearchHit {
        /**
         * 文件路径。
         */
        private String filePath;
        /**
         * 相似度分数。
         */
        private Double score;
        /**
         * 片段内容。
         */
        private String content;

        public RepoSearchHit(String filePath, Double score, String content) {
            this.filePath = filePath;
            this.score = score;
            this.content = content;
        }

        public String getFilePath() {
            return filePath;
        }

        public Double getScore() {
            return score;
        }

        public String getContent() {
            return content;
        }
    }
}
