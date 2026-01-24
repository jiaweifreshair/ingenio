package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交互式分析会话实体
 *
 * 用于管理AI深度思考的交互式分析流程,支持:
 * - 每个步骤完成后等待人工确认
 * - 用户提出修改建议后重新执行当前步骤
 * - 完整的会话状态跟踪
 */
@Data
@TableName(value = "interactive_analysis_sessions", autoResultMap = true)
public class InteractiveAnalysisSessionEntity {

    /**
     * 会话ID (UUID)
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 原始需求描述
     */
    private String requirement;

    /**
     * 当前执行的步骤 (1-6)
     */
    private Integer currentStep;

    /**
     * 会话状态
     * - RUNNING: 当前步骤正在执行
     * - WAITING_CONFIRMATION: 当前步骤完成,等待用户确认
     * - COMPLETED: 所有步骤完成
     * - FAILED: 执行失败
     * - CANCELLED: 用户取消
     */
    private String status;

    /**
     * 每个步骤的执行结果 (JSON格式)
     * key: 步骤编号 (1-6)
     * value: 步骤执行结果对象
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Integer, Object> stepResults;

    /**
     * 每个步骤的用户反馈 (JSON格式)
     * key: 步骤编号 (1-6)
     * value: 用户修改建议
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Integer, String> stepFeedback;

    /**
     * 每个步骤的重试次数 (JSON格式)
     * key: 步骤编号 (1-6)
     * value: 重试次数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Integer, Integer> stepRetries;

    /**
     * 最终分析结果 (JSON格式)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object finalResult;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * Step 6 生成的技术蓝图 Markdown
     * 
     * 用途：
     * - 透传给 OpenLovable-CN 前端生成
     * - 透传给 G3 Engine 后端生成
     * - Session 级别存储，无需持久化到文件
     */
    private String blueprintMarkdown;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
