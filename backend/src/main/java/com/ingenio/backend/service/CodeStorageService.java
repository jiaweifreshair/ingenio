package com.ingenio.backend.service;

import com.ingenio.backend.dto.CodeRetrievalResult;
import com.ingenio.backend.dto.CodeStorageResult;

import java.util.UUID;

/**
 * 代码存储服务
 * 负责生成代码的持久化存储（归档、Git仓库等）
 */
public interface CodeStorageService {

    /**
     * 保存生成的代码
     * @param appSpecId AppSpec ID
     * @param frontendCodePath 前端代码路径
     * @param backendCodePath 后端代码路径
     * @return 代码存储结果
     */
    CodeStorageResult saveGeneratedCode(UUID appSpecId, String frontendCodePath, String backendCodePath);

    /**
     * 获取代码
     * @param appSpecId AppSpec ID
     * @return 代码获取结果
     */
    CodeRetrievalResult getCode(UUID appSpecId);

    /**
     * 归档代码为ZIP文件
     * @param appSpecId AppSpec ID
     * @param codePath 代码路径
     * @param codeType 代码类型（frontend/backend）
     * @return 归档文件路径
     */
    String archiveCode(UUID appSpecId, String codePath, String codeType);
}
