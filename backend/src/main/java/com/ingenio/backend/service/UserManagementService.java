package com.ingenio.backend.service;

import java.time.Instant;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.dto.user.*;
import com.ingenio.backend.entity.UserDeviceEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.entity.UserLogEntity;
import com.ingenio.backend.mapper.UserDeviceMapper;
import com.ingenio.backend.mapper.UserLogMapper;
import com.ingenio.backend.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 * 提供用户信息管理、设备管理、操作日志等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserMapper userMapper;
    private final UserDeviceMapper userDeviceMapper;
    private final UserLogMapper userLogMapper;
    private final MinioService minioService;
    private final HttpServletRequest request;

    /**
     * 获取用户信息
     *
     * @return 用户信息响应
     */
    public UserProfileResponse getUserProfile() {
        String userId = StpUtil.getLoginIdAsString();
        UserEntity user = userMapper.findByIdWithCast(userId).orElse(null);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return convertToProfileResponse(user);
    }

    /**
     * 更新用户信息
     *
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        String userId = StpUtil.getLoginIdAsString();
        UserEntity user = userMapper.findByIdWithCast(userId).orElse(null);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 更新字段
        // 1. 更新用户名（需要检查唯一性）
        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            // 检查新用户名是否已被使用
            var existingUser = userMapper.findByUsername(request.getUsername());
            if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                throw new RuntimeException("用户名已被使用");
            }
            String oldUsername = user.getUsername();
            user.setUsername(request.getUsername());
            log.info("用户名已更新: userId={}, oldUsername={}, newUsername={}",
                    userId, oldUsername, request.getUsername());
        }
        // 2. 更新显示名称
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        // 3. 更新邮箱（需要重置验证状态）
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // TODO: 验证邮箱是否已被使用
            user.setEmail(request.getEmail());
            user.setEmailVerified(false); // 重置验证状态
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
            if (!request.getPhone().equals(user.getPhone())) {
                user.setPhoneVerified(false); // 重置验证状态
            }
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        user.setUpdatedAt(Instant.now());

        int updated = userMapper.updateByIdWithCast(user);
        if (updated == 0) {
            throw new RuntimeException("更新用户信息失败");
        }

        log.info("用户信息更新成功: userId={}", userId);

        return convertToProfileResponse(user);
    }

    /**
     * 上传头像
     *
     * @param file 头像文件
     * @return 头像URL
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(MultipartFile file) {
        String userId = StpUtil.getLoginIdAsString();

        // 1. 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只支持图片格式");
        }

        // 2. 验证文件大小（最大2MB）
        long maxSize = 2 * 1024 * 1024; // 2MB
        if (file.getSize() > maxSize) {
            throw new RuntimeException("头像文件大小不能超过2MB");
        }

        try {
            // 3. 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";
            String objectName = "avatars/" + userId + "/" + System.currentTimeMillis() + extension;

            // 4. 上传到MinIO
            InputStream inputStream = file.getInputStream();
            String avatarUrl = minioService.uploadFile(objectName, inputStream, contentType, file.getSize());

            // 5. 更新用户头像URL
            UserEntity user = userMapper.findByIdWithCast(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            user.setAvatarUrl(avatarUrl);
            user.setUpdatedAt(Instant.now());

            int updated = userMapper.updateByIdWithCast(user);
            if (updated == 0) {
                throw new RuntimeException("更新头像URL失败");
            }

            log.info("用户头像上传成功: userId={}, avatarUrl={}", userId, avatarUrl);

            return avatarUrl;
        } catch (Exception e) {
            log.error("头像上传失败: userId={}", userId, e);
            throw new RuntimeException("头像上传失败: " + e.getMessage());
        }
    }

    /**
     * 修改密码
     *
     * @param request 修改密码请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        String userId = StpUtil.getLoginIdAsString();
        UserEntity user = userMapper.findByIdWithCast(userId).orElse(null);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 1. 验证当前密码
        if (!BCrypt.checkpw(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("当前密码错误");
        }

        // 2. 验证新密码一致性
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 3. 验证新密码不能与当前密码相同
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }

        // 4. 更新密码
        user.setPasswordHash(BCrypt.hashpw(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());

        int updated = userMapper.updateByIdWithCast(user);
        if (updated == 0) {
            throw new RuntimeException("修改密码失败");
        }

        log.info("用户密码修改成功: userId={}", userId);
    }

    /**
     * 获取用户登录设备列表
     *
     * @return 设备列表
     */
    public List<LoginDeviceResponse> getLoginDevices() {
        String userId = StpUtil.getLoginIdAsString();
        List<UserDeviceEntity> devices = userDeviceMapper.findByUserId(UUID.fromString(userId));

        return devices.stream()
                .map(this::convertToDeviceResponse)
                .collect(Collectors.toList());
    }

    /**
     * 移除登录设备
     *
     * @param deviceId 设备ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeLoginDevice(String deviceId) {
        String userId = StpUtil.getLoginIdAsString();

        UserDeviceEntity device = userDeviceMapper.selectById(deviceId);
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        if (!device.getUserId().toString().equals(userId)) {
            throw new RuntimeException("无权删除该设备");
        }

        // 如果设备有tokenId，则踢出该设备的登录
        if (device.getTokenId() != null) {
            try {
                StpUtil.kickoutByTokenValue(device.getTokenId());
                log.info("踢出设备登录: deviceId={}, tokenId={}", deviceId, device.getTokenId());
            } catch (Exception e) {
                log.warn("踢出设备登录失败: {}", e.getMessage());
            }
        }

        int deleted = userDeviceMapper.deleteByIdAndUserId(UUID.fromString(deviceId), UUID.fromString(userId));
        if (deleted == 0) {
            throw new RuntimeException("删除设备失败");
        }

        log.info("用户设备删除成功: userId={}, deviceId={}", userId, deviceId);
    }

    /**
     * 获取用户操作日志（分页）
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param category 操作分类（可选）
     * @return 分页结果
     */
    public Page<UserLogResponse> getUserLogs(int pageNum, int pageSize, String category) {
        String userId = StpUtil.getLoginIdAsString();
        Page<UserLogEntity> page = new Page<>(pageNum, pageSize);

        IPage<UserLogEntity> result;
        if (category != null && !category.isEmpty()) {
            result = userLogMapper.findByUserIdAndCategoryWithPage(page, UUID.fromString(userId), category);
        } else {
            result = userLogMapper.findByUserIdWithPage(page, UUID.fromString(userId));
        }

        // 转换为响应DTO
        Page<UserLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<UserLogResponse> records = result.getRecords().stream()
                .map(this::convertToLogResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(records);

        return responsePage;
    }

    /**
     * 记录用户操作日志
     *
     * @param action         操作类型
     * @param actionCategory 操作分类
     * @param description    操作描述
     * @param resourceType   资源类型
     * @param resourceId     资源ID
     */
    public void logUserAction(String action, String actionCategory, String description,
                               String resourceType, String resourceId) {
        String userId = StpUtil.getLoginIdAsString();

        UserLogEntity userLog = UserLogEntity.builder()
                .userId(UUID.fromString(userId))
                .action(action)
                .actionCategory(actionCategory)
                .description(description)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .ipAddress(getClientIp())
                .userAgent(request.getHeader("User-Agent"))
                .requestMethod(request.getMethod())
                .requestPath(request.getRequestURI())
                .status(UserLogEntity.Status.SUCCESS.getValue())
                .build();

        userLogMapper.insert(userLog);

        log.debug("记录用户操作日志: userId={}, action={}", userId, action);
    }

    /**
     * 将UserEntity转换为UserProfileResponse
     */
    private UserProfileResponse convertToProfileResponse(UserEntity user) {
        UserProfileResponse response = new UserProfileResponse();
        BeanUtils.copyProperties(user, response);
        response.setId(user.getId().toString());
        return response;
    }

    /**
     * 将UserDeviceEntity转换为LoginDeviceResponse
     */
    private LoginDeviceResponse convertToDeviceResponse(UserDeviceEntity device) {
        LoginDeviceResponse response = new LoginDeviceResponse();
        BeanUtils.copyProperties(device, response);
        response.setId(device.getId().toString());
        return response;
    }

    /**
     * 将UserLogEntity转换为UserLogResponse
     */
    private UserLogResponse convertToLogResponse(UserLogEntity log) {
        UserLogResponse response = new UserLogResponse();
        BeanUtils.copyProperties(log, response);
        response.setId(log.getId().toString());
        return response;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
