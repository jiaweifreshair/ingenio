package com.ingenio.backend.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心控制器
 */
@Slf4j
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "通知中心", description = "消息通知相关接口")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 获取未读通知数量
     * 支持匿名调用（返回0），已登录用户返回真实数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数", description = "轮询接口，支持匿名调用")
    public Result<Long> getUnreadCount() {
        // 如果用户未登录，直接返回 0 (匿名降级)
        if (!StpUtil.isLogin()) {
            return Result.success(0L);
        }

        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(notificationService.getUnreadCount(userId));
    }
}
