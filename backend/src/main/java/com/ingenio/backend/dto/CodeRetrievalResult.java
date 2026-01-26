package com.ingenio.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码获取结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeRetrievalResult {

    /**
     * 前端代码归档路径
     */
    private String frontendArchivePath;

    /**
     * 后端代码归档路径
     */
    private String backendArchivePath;

    /**
     * 前端代码URL（如果有）
     */
    private String frontendCodeUrl;

    /**
     * 后端代码URL（如果有）
     */
    private String backendCodeUrl;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;
}
