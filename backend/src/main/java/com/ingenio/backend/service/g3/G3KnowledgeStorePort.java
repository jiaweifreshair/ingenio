package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;

import java.util.List;
import java.util.UUID;

/**
 * G3 知识库访问端口。
 *
 * 是什么：统一的知识库访问接口。
 * 做什么：封装索引与检索能力，屏蔽实现差异。
 * 为什么：支持 Spring AI 与 LangChain4j 的可替换实现。
 */
public interface G3KnowledgeStorePort {

    /**
     * 写入任务级索引。
     *
     * 是什么：任务级索引入口。
     * 做什么：将生成产物切片并写入向量库。
     * 为什么：为修复与生成提供检索上下文。
     *
     * @param job       G3 任务
     * @param artifacts 产物列表
     */
    void ingest(G3JobEntity job, List<G3ArtifactEntity> artifacts);

    /**
     * 写入仓库级索引。
     *
     * 是什么：仓库索引入口。
     * 做什么：将仓库文档写入向量库。
     * 为什么：支持跨任务的上下文检索。
     *
     * @param documents 文档列表（实现方自定义类型）
     * @return 写入的切片数量
     */
    int ingestRepo(List<?> documents);

    /**
     * 任务级语义检索。
     *
     * 是什么：任务范围检索方法。
     * 做什么：返回与查询相关的文档片段。
     * 为什么：提升修复与生成准确率。
     *
     * @param query 查询语句
     * @param jobId 任务ID
     * @param topK  返回数量
     * @return 文档片段列表
     */
    List<?> search(String query, UUID jobId, int topK);

    /**
     * 仓库级语义检索。
     *
     * 是什么：仓库范围检索方法。
     * 做什么：按租户/项目过滤检索结果。
     * 为什么：支持资产复用与跨任务修复。
     *
     * @param query     查询语句
     * @param tenantId  租户ID
     * @param projectId 项目ID
     * @param topK      返回数量
     * @return 文档片段列表
     */
    List<?> searchRepo(String query, UUID tenantId, UUID projectId, int topK);

    /**
     * 格式化检索结果。
     *
     * 是什么：检索结果格式化方法。
     * 做什么：将文档片段转换为 Prompt 上下文。
     * 为什么：提升模型理解与可读性。
     *
     * @param documents 文档片段列表
     * @return 格式化后的文本
     */
    String formatForContext(List<?> documents);
}
