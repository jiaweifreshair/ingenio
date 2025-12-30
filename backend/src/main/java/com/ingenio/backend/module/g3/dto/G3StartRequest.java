package com.ingenio.backend.module.g3.dto;

import lombok.Data;

@Data
public class G3StartRequest {
    private String requirement;
    private String tenantId;
    /**
     * Scout推荐模版上下文（Intelligent Discovery Result）
     */
    private String templateContext;
}
