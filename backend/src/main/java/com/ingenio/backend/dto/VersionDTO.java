package com.ingenio.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 版本信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDTO {

    private UUID id;
    private Integer version;
    private UUID parentVersionId;
    private String status;
    private Integer qualityScore;
    private String intentType;
    private String selectedStyle;
    private Boolean designConfirmed;
    private Instant createdAt;
    private Instant updatedAt;
}
