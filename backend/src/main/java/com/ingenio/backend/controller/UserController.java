package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.user.*;
import com.ingenio.backend.service.ApiKeyManagementService;
import com.ingenio.backend.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户管理控制器
 * 提供用户信息管理、设备管理、操作日志、API密钥管理等接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
@SaCheckLogin
@Tag(name = "用户管理", description = "用户信息管理、设备管理、操作日志、API密钥管理等接口")
public class UserController {

    private final UserManagementService userManagementService;
    private final ApiKeyManagementService apiKeyManagementService;

    // ==================== 用户信息管理 ====================

    /**
     * 获取当前用户信息
     */
    @GetMapping("/profile")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    public Result<UserProfileResponse> getProfile() {
        UserProfileResponse profile = userManagementService.getUserProfile();
        return Result.success(profile);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/profile")
    @Operation(summary = "更新用户信息", description = "更新用户的显示名称、邮箱、手机号等信息")
    public Result<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse profile = userManagementService.updateProfile(request);
        return Result.success(profile);
    }

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    @Operation(summary = "上传头像", description = "上传用户头像图片（最大2MB，支持jpg/png格式）")
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        String avatarUrl = userManagementService.uploadAvatar(file);
        return Result.success(avatarUrl);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改当前用户密码（需验证当前密码）")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userManagementService.changePassword(request);
        return Result.successMessage("密码修改成功");
    }

    // ==================== 登录设备管理 ====================

    /**
     * 获取登录设备列表
     */
    @GetMapping("/devices")
    @Operation(summary = "获取登录设备列表", description = "获取当前用户所有登录设备")
    public Result<List<LoginDeviceResponse>> getLoginDevices() {
        List<LoginDeviceResponse> devices = userManagementService.getLoginDevices();
        return Result.success(devices);
    }

    /**
     * 移除登录设备
     */
    @DeleteMapping("/devices/{deviceId}")
    @Operation(summary = "移除登录设备", description = "移除指定登录设备并踢出该设备的登录状态")
    public Result<Void> removeLoginDevice(@PathVariable String deviceId) {
        userManagementService.removeLoginDevice(deviceId);
        return Result.successMessage("设备移除成功");
    }

    // ==================== 操作日志 ====================

    /**
     * 获取用户操作日志（分页）
     */
    @GetMapping("/logs")
    @Operation(summary = "获取操作日志", description = "分页查询用户操作日志")
    public Result<Page<UserLogResponse>> getUserLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category) {
        Page<UserLogResponse> logs = userManagementService.getUserLogs(pageNum, pageSize, category);
        return Result.success(logs);
    }

    // ==================== API密钥管理 ====================

    /**
     * 获取API密钥列表
     */
    @GetMapping("/api-keys")
    @Operation(summary = "获取API密钥列表", description = "获取当前用户所有API密钥")
    public Result<List<ApiKeyResponse>> listApiKeys() {
        List<ApiKeyResponse> apiKeys = apiKeyManagementService.listApiKeys();
        return Result.success(apiKeys);
    }

    /**
     * 生成新的API密钥
     */
    @PostMapping("/api-keys")
    @Operation(summary = "生成API密钥", description = "创建新的API密钥（完整密钥仅返回一次，请妥善保管）")
    public Result<ApiKeyResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyResponse apiKey = apiKeyManagementService.generateApiKey(request);
        return Result.success(apiKey);
    }

    /**
     * 删除API密钥
     */
    @DeleteMapping("/api-keys/{keyId}")
    @Operation(summary = "删除API密钥", description = "删除指定的API密钥")
    public Result<Void> deleteApiKey(@PathVariable String keyId) {
        apiKeyManagementService.deleteApiKey(keyId);
        return Result.successMessage("API密钥删除成功");
    }
}
