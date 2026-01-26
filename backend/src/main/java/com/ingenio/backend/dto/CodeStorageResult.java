package com.ingenio.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码存储结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeStorageResult {

    /**
     * 前端代码归档路径
     */
    private String frontendArchivePath;

    /**
     * 后端代码归档路径
     */
    private String backendArchivePath;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;
}
