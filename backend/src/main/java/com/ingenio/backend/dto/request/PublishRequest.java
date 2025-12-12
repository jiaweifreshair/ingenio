package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 多端发布请求DTO
 *
 * 功能：
 * - 接收用户选择的发布平台列表
 * - 接收每个平台的配置参数
 * - 关联到具体的项目/AppSpec
 *
 * 使用场景：
 * - 预览页面的发布功能
 * - 项目管理页的批量发布
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishRequest {

    /**
     * 项目ID
     * 必填，标识要发布的项目
     */
    @NotNull(message = "项目ID不能为空")
    private String projectId;

    /**
     * 发布平台列表
     * 必填，至少选择一个平台
     * 可选值：android, ios, h5, miniapp, desktop
     */
    @NotEmpty(message = "至少选择一个发布平台")
    private List<String> platforms;

    /**
     * 平台配置参数
     * 必填，包含每个平台的配置信息
     * Key: 平台类型（android, ios等）
     * Value: 平台配置对象
     *
     * Android配置示例：
     * {
     *   "packageName": "com.ingenio.app",
     *   "appName": "IngenioApp",
     *   "versionName": "1.0.0",
     *   "versionCode": 1,
     *   "minSdkVersion": 24,
     *   "targetSdkVersion": 34
     * }
     *
     * iOS配置示例：
     * {
     *   "bundleId": "com.ingenio.app",
     *   "appName": "IngenioApp",
     *   "versionName": "1.0.0",
     *   "buildNumber": "1",
     *   "teamId": "ABCD1234",
     *   "minIosVersion": "13.0"
     * }
     */
    @NotNull(message = "平台配置不能为空")
    private Map<String, Map<String, Object>> platformConfigs;

    /**
     * 是否并行构建
     * 默认true，所有平台并行构建
     */
    @Builder.Default
    private Boolean parallelBuild = true;

    /**
     * 构建优先级
     * 可选值：LOW, NORMAL, HIGH
     * 默认NORMAL
     */
    @Builder.Default
    private String priority = "NORMAL";
}
