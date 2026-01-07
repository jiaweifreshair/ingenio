package com.ingenio.backend.entity.g3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * G3引擎执行日志条目
 * 嵌入在G3JobEntity的logs字段中（JSONB数组）
 *
 * 与前端 types/g3.ts 中的 G3LogEntry 保持一致
 *
 * 注意：使用 @JsonIgnoreProperties 忽略未知字段，
 * 以兼容旧版数据库记录中可能存在的额外字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class G3LogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 时间戳（ISO 8601格式）
     */
    private String timestamp;

    /**
     * 角色（PLAYER/COACH/EXECUTOR/ARCHITECT）
     * @see Role
     */
    private String role;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 日志级别（info/warn/error/success）
     * @see Level
     */
    private String level;

    /**
     * 角色枚举
     * - PLAYER: 代码生成器（Coder Agent）
     * - COACH: 修复教练（Coach Agent）
     * - EXECUTOR: 执行器（Sandbox/Compiler）
     * - ARCHITECT: 架构师（Architect Agent）
     * - SYSTEM: 系统消息（心跳、连接状态等）
     */
    public enum Role {
        PLAYER("PLAYER", "代码生成器"),
        COACH("COACH", "修复教练"),
        EXECUTOR("EXECUTOR", "执行器"),
        ARCHITECT("ARCHITECT", "架构师"),
        SYSTEM("SYSTEM", "系统");

        private final String value;
        private final String description;

        Role(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static Role fromValue(String value) {
            for (Role role : Role.values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Unknown G3 role: " + value);
        }
    }

    /**
     * 日志级别枚举
     */
    public enum Level {
        INFO("info", "信息"),
        WARN("warn", "警告"),
        ERROR("error", "错误"),
        SUCCESS("success", "成功"),
        HEARTBEAT("heartbeat", "心跳");

        private final String value;
        private final String description;

        Level(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static Level fromValue(String value) {
            for (Level level : Level.values()) {
                if (level.value.equals(value)) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown G3 log level: " + value);
        }
    }

    /**
     * 创建信息级别日志
     */
    public static G3LogEntry info(Role role, String message) {
        return G3LogEntry.builder()
            .timestamp(java.time.Instant.now().toString())
            .role(role.getValue())
            .message(message)
            .level(Level.INFO.getValue())
            .build();
    }

    /**
     * 创建警告级别日志
     */
    public static G3LogEntry warn(Role role, String message) {
        return G3LogEntry.builder()
            .timestamp(java.time.Instant.now().toString())
            .role(role.getValue())
            .message(message)
            .level(Level.WARN.getValue())
            .build();
    }

    /**
     * 创建错误级别日志
     */
    public static G3LogEntry error(Role role, String message) {
        return G3LogEntry.builder()
            .timestamp(java.time.Instant.now().toString())
            .role(role.getValue())
            .message(message)
            .level(Level.ERROR.getValue())
            .build();
    }

    /**
     * 创建成功级别日志
     */
    public static G3LogEntry success(Role role, String message) {
        return G3LogEntry.builder()
            .timestamp(java.time.Instant.now().toString())
            .role(role.getValue())
            .message(message)
            .level(Level.SUCCESS.getValue())
            .build();
    }

    /**
     * 创建心跳日志（用于保持 SSE 连接活跃）
     * 前端应过滤掉心跳消息，不在 UI 中显示
     */
    public static G3LogEntry heartbeat() {
        return G3LogEntry.builder()
            .timestamp(java.time.Instant.now().toString())
            .role(Role.SYSTEM.getValue())
            .message("heartbeat")
            .level(Level.HEARTBEAT.getValue())
            .build();
    }

    /**
     * 判断是否为心跳消息
     */
    public boolean isHeartbeat() {
        return Level.HEARTBEAT.getValue().equals(this.level);
    }
}
