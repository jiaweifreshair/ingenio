package com.ingenio.backend.service.impl;

import com.ingenio.backend.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 通知服务实现类
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public long getUnreadCount(UUID userId) {
        // TODO: 对接真实的通知数据库表 (notifications)
        // 目前仅做 Mock 实现，返回 0，避免前端轮询报错
        log.debug("获取用户未读通知数量: userId={}", userId);
        return 0;
    }
}
