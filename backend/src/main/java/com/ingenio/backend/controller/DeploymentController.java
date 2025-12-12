package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.deployment.DeploymentAutomationService;
import com.ingenio.backend.deployment.DeploymentAutomationService.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 部署自动化API控制器
 *
 * <p>功能：</p>
 * <ol>
 *   <li>生成部署配置（Docker/Vercel/AWS/K8s）</li>
 *   <li>执行部署</li>
 *   <li>查询部署状态</li>
 *   <li>执行回滚</li>
 * </ol>
 *
 * @author Justin
 * @since 2025-11-17 V2.0部署自动化
 */
@RestController
@RequestMapping("/v1/deployment")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentAutomationService deploymentService;

    /**
     * 生成部署配置
     *
     * @param request 配置请求
     * @return 完整的部署配置
     */
    @PostMapping("/config")
    @SaCheckLogin
    public Result<DeploymentConfig> generateConfig(@RequestBody GenerateConfigRequest request) {
        DeploymentConfig config = deploymentService.generateConfig(
                request.getAppName(),
                request.getEnvironment(),
                request.getTarget(),
                request.getCodeArtifact()
        );
        return Result.success(config);
    }

    /**
     * 执行部署
     *
     * @param request 部署请求
     * @return 部署任务
     */
    @PostMapping("/deploy")
    @SaCheckLogin
    public Result<DeploymentTask> deploy(@RequestBody DeployRequest request) {
        // 先生成配置
        DeploymentConfig config = deploymentService.generateConfig(
                request.getAppName(),
                request.getEnvironment(),
                request.getTarget(),
                request.getCodeArtifact()
        );

        // 执行部署
        DeploymentTask task = deploymentService.deploy(config);
        return Result.success(task);
    }

    /**
     * 查询部署状态
     *
     * @param deploymentId 部署ID
     * @return 部署任务状态
     */
    @GetMapping("/status/{deploymentId}")
    @SaCheckLogin
    public Result<DeploymentTask> getStatus(@PathVariable String deploymentId) {
        DeploymentTask task = deploymentService.getDeploymentStatus(deploymentId);
        if (task == null) {
            return Result.error("部署任务不存在: " + deploymentId);
        }
        return Result.success(task);
    }

    /**
     * 执行回滚
     *
     * @param deploymentId  原部署ID
     * @param targetVersion 目标版本
     * @return 回滚任务
     */
    @PostMapping("/rollback/{deploymentId}")
    @SaCheckLogin
    public Result<DeploymentTask> rollback(
            @PathVariable String deploymentId,
            @RequestParam String targetVersion
    ) {
        DeploymentTask task = deploymentService.rollback(deploymentId, targetVersion);
        return Result.success(task);
    }

    /**
     * 获取支持的环境列表
     */
    @GetMapping("/environments")
    public Result<Environment[]> getEnvironments() {
        return Result.success(Environment.values());
    }

    /**
     * 获取支持的部署目标列表
     */
    @GetMapping("/targets")
    public Result<DeploymentTarget[]> getTargets() {
        return Result.success(DeploymentTarget.values());
    }

    // ==================== 请求DTO ====================

    @Data
    public static class GenerateConfigRequest {
        private String appName;
        private Environment environment;
        private DeploymentTarget target;
        private Map<String, Object> codeArtifact;
    }

    @Data
    public static class DeployRequest {
        private String appName;
        private Environment environment;
        private DeploymentTarget target;
        private Map<String, Object> codeArtifact;
    }
}
