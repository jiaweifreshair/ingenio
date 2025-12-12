package com.ingenio.backend.service;

/**
 * 通知服务接口
 * 负责处理系统通知、消息提醒等
 */
public interface NotificationService {

    /**
     * 获取指定用户的未读通知数量
     * @param userId 用户ID
     * @return 未读数量
     */
    long getUnreadCount(Long userId);
}
