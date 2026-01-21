package com.ingenio.backend.entity.g3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * G3引擎执行日志条目
 * 嵌入在G3JobEntity的logs字段中（JSONB数组）
 */
@Data
@Builder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class G3LogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 时间戳（ISO 8601格式）
     */
    private String timestamp;

    /**
     * 角色（PLAYER/COACH/EXECUTOR/ARCHITECT）
     * 
     * @see Role
     */
    private String role;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 日志级别（info/warn/error/success）
     * 
     * @see Level
     */
    private String level;

    // Manual Boilerplate
    public G3LogEntry(String timestamp, String role, String message, String level) {
        this.timestamp = timestamp;
        this.role = role;
        this.message = message;
        this.level = level;
    }

    public static G3LogEntryBuilder builder() {
        return new G3LogEntryBuilder();
    }

    public static class G3LogEntryBuilder {
        private String timestamp;
        private String role;
        private String message;
        private String level;

        G3LogEntryBuilder() {
        }

        public G3LogEntryBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public G3LogEntryBuilder role(String role) {
            this.role = role;
            return this;
        }

        public G3LogEntryBuilder message(String message) {
            this.message = message;
            return this;
        }

        public G3LogEntryBuilder level(String level) {
            this.level = level;
            return this;
        }

        public G3LogEntry build() {
            return new G3LogEntry(timestamp, role, message, level);
        }
    }

    // Getters and Setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

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

    public static G3LogEntry info(Role role, String message) {
        return G3LogEntry.builder()
                .timestamp(java.time.Instant.now().toString())
                .role(role.getValue())
                .message(message)
                .level(Level.INFO.getValue())
                .build();
    }

    public static G3LogEntry warn(Role role, String message) {
        return G3LogEntry.builder()
                .timestamp(java.time.Instant.now().toString())
                .role(role.getValue())
                .message(message)
                .level(Level.WARN.getValue())
                .build();
    }

    public static G3LogEntry error(Role role, String message) {
        return G3LogEntry.builder()
                .timestamp(java.time.Instant.now().toString())
                .role(role.getValue())
                .message(message)
                .level(Level.ERROR.getValue())
                .build();
    }

    public static G3LogEntry success(Role role, String message) {
        return G3LogEntry.builder()
                .timestamp(java.time.Instant.now().toString())
                .role(role.getValue())
                .message(message)
                .level(Level.SUCCESS.getValue())
                .build();
    }

    public static G3LogEntry heartbeat() {
        return G3LogEntry.builder()
                .timestamp(java.time.Instant.now().toString())
                .role(Role.SYSTEM.getValue())
                .message("heartbeat")
                .level(Level.HEARTBEAT.getValue())
                .build();
    }

    public boolean isHeartbeat() {
        return Level.HEARTBEAT.getValue().equals(this.level);
    }
}
