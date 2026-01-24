package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 前端 API Client 生成器
 *
 * 职责：
 * 基于后端生成的 OpenAPI 契约（openapi.yaml），使用 AI 生成 TypeScript API Client 代码。
 * 让前端可以直接调用后端 API，实现前后端联调。
 *
 * 生成产物：
 * - api-client/index.ts: API 客户端入口
 * - api-client/types.ts: TypeScript 类型定义（DTO）
 * - api-client/endpoints/*.ts: 各端点的调用方法
 *
 * 设计考量：
 * - 使用 AI 生成而非 orval CLI，因为沙箱环境可能没有 Node.js
 * - 生成的代码使用 fetch API，无额外依赖
 * - 支持 TanStack Query 风格的 hooks（可选）
 *
 * @author Ingenio G3 Engine
 * @since M2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontendApiClientGenerator {

    private final ChatLanguageModel chatLanguageModel;

    /**
     * 基于 OpenAPI 契约生成 TypeScript API Client
     *
     * @param job     G3 任务（包含 contractYaml）
     * @param baseUrl API 基础 URL
     * @return 生成的产物列表
     */
    public List<G3ArtifactEntity> generate(G3JobEntity job, String baseUrl) {
        String contractYaml = job.getContractYaml();
        if (contractYaml == null || contractYaml.isBlank()) {
            log.warn("FrontendApiClientGenerator: contractYaml 为空，跳过生成");
            return List.of();
        }

        log.info("开始生成前端 API Client: jobId={}, contractLength={}",
                job.getId(), contractYaml.length());

        List<G3ArtifactEntity> artifacts = new ArrayList<>();

        try {
            // 1. 生成类型定义
            String typesContent = generateTypes(contractYaml);
            artifacts.add(createArtifact(job, "types.ts",
                    "frontend/src/api-client/types.ts", typesContent, "typescript"));

            // 2. 生成 API 客户端
            String clientContent = generateApiClient(contractYaml, baseUrl);
            artifacts.add(createArtifact(job, "api-client.ts",
                    "frontend/src/api-client/api-client.ts", clientContent, "typescript"));

            // 3. 生成入口文件
            String indexContent = generateIndex();
            artifacts.add(createArtifact(job, "index.ts",
                    "frontend/src/api-client/index.ts", indexContent, "typescript"));

            log.info("前端 API Client 生成完成: 共 {} 个文件", artifacts.size());

        } catch (Exception e) {
            log.error("生成前端 API Client 失败", e);
            // 返回空列表，不阻塞主流程
        }

        return artifacts;
    }

    /**
     * 使用 AI 生成 TypeScript 类型定义
     */
    private String generateTypes(String contractYaml) {
        String prompt = String.format("""
                你是一个 TypeScript 专家。请基于以下 OpenAPI 契约，生成 TypeScript 类型定义。

                要求：
                1. 为每个 schema 组件生成对应的 TypeScript interface
                2. 使用 export interface 导出
                3. 字段使用可选符号 (?) 如果不是 required
                4. 添加简洁的 JSDoc 注释
                5. 只输出代码，不要任何解释

                OpenAPI 契约：
                ```yaml
                %s
                ```

                请输出 types.ts 文件内容：
                """, truncateContract(contractYaml));

        String response = chatLanguageModel.generate(prompt);
        return extractCode(response);
    }

    /**
     * 使用 AI 生成 API 客户端代码
     */
    private String generateApiClient(String contractYaml, String baseUrl) {
        String prompt = String.format("""
                你是一个 TypeScript 专家。请基于以下 OpenAPI 契约，生成 API 客户端代码。

                要求：
                1. 使用 fetch API，不依赖 axios
                2. 为每个端点生成对应的调用方法
                3. 方法名使用 camelCase（如 getUserById, createOrder）
                4. 导入 types.ts 中的类型
                5. 支持错误处理，抛出统一的 ApiError
                6. 基础 URL 使用参数: %s
                7. 只输出代码，不要任何解释

                OpenAPI 契约：
                ```yaml
                %s
                ```

                请输出 api-client.ts 文件内容：
                """, baseUrl, truncateContract(contractYaml));

        String response = chatLanguageModel.generate(prompt);
        return extractCode(response);
    }

    /**
     * 生成入口文件
     */
    private String generateIndex() {
        return """
                /**
                 * API Client 入口
                 * 自动生成，请勿手动修改
                 */
                export * from './types';
                export * from './api-client';
                """;
    }

    /**
     * 创建产物实体
     */
    private G3ArtifactEntity createArtifact(
            G3JobEntity job,
            String fileName,
            String filePath,
            String content,
            String language) {
        G3ArtifactEntity artifact = new G3ArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setJobId(job.getId());
        artifact.setFileName(fileName);
        artifact.setFilePath(filePath);
        artifact.setContent(content);
        artifact.setLanguage(language);
        artifact.setArtifactType("frontend-api-client");
        artifact.setGeneratedBy("FrontendApiClientGenerator");
        artifact.setGenerationRound(0);
        artifact.setVersion(1);
        artifact.setCreatedAt(java.time.Instant.now());
        return artifact;
    }

    /**
     * 截断契约以避免超出 token 限制
     */
    private String truncateContract(String contract) {
        int maxLength = 8000;
        if (contract.length() <= maxLength) {
            return contract;
        }
        return contract.substring(0, maxLength) + "\n# ... (已截断)";
    }

    /**
     * 从 AI 响应中提取代码
     */
    private String extractCode(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }

        // 移除 markdown 代码块标记
        String code = response
                .replaceAll("```typescript\\s*", "")
                .replaceAll("```ts\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        return code;
    }
}
