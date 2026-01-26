package com.ingenio.backend.controller;

import com.ingenio.backend.dto.CreateVersionRequest;
import com.ingenio.backend.dto.VersionCompareResult;
import com.ingenio.backend.dto.VersionDTO;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 版本管理控制器
 */
@RestController
@RequestMapping("/api/v1/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @PostMapping
    public ResponseEntity<AppSpecEntity> createVersion(@RequestBody CreateVersionRequest request) {
        AppSpecEntity version = versionService.createVersion(request);
        return ResponseEntity.ok(version);
    }

    @GetMapping
    public ResponseEntity<List<VersionDTO>> listVersions(
            @RequestParam UUID tenantId,
            @RequestParam UUID userId) {
        List<VersionDTO> versions = versionService.listVersions(tenantId, userId);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<VersionDTO> getVersion(@PathVariable UUID versionId) {
        VersionDTO version = versionService.getVersion(versionId);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/{sourceVersionId}/compare/{targetVersionId}")
    public ResponseEntity<VersionCompareResult> compareVersions(
            @PathVariable UUID sourceVersionId,
            @PathVariable UUID targetVersionId) {
        VersionCompareResult result = versionService.compareVersions(sourceVersionId, targetVersionId);
        return ResponseEntity.ok(result);
    }
}
