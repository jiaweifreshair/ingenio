package com.ingenio.backend.agent;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * ExecuteGuard检查报告
 *
 * 包含所有前置条件检查项的状态，用于：
 * 1. 前端展示检查进度和状态
 * 2. 帮助用户了解哪些步骤还未完成
 * 3. 提供清晰的阻塞原因说明
 */
@Data
@Builder
public class ExecuteGuardReport {

    /**
     * AppSpec是否存在
     */
    private boolean appSpecExists;

    /**
     * 意图是否已识别
     */
    private boolean intentClassified;

    /**
     * 设计风格是否已选择
     */
    private boolean styleSelected;

    /**
     * 前端原型代码是否存在
     */
    private boolean frontendPrototypeExists;

    /**
     * 原型预览URL是否可访问
     */
    private boolean prototypeUrlAccessible;

    /**
     * 用户是否已确认设计方案
     */
    private boolean designConfirmed;

    /**
     * 所有检查是否全部通过
     */
    private boolean allChecksPassed;

    /**
     * 意图类型（如：CLONE_EXISTING_WEBSITE）
     */
    private String intentType;

    /**
     * 选择的设计风格（如：A-现代极简）
     */
    private String selectedStyle;

    /**
     * 原型预览URL
     */
    private String prototypeUrl;

    /**
     * 设计确认时间
     */
    private Instant designConfirmedAt;

    /**
     * 获取阻塞原因描述
     *
     * @return 第一个未通过检查项的描述，如果全部通过则返回null
     */
    public String getBlockingReason() {
        if (!appSpecExists) {
            return "AppSpec不存在，请先创建项目";
        }
        if (!intentClassified) {
            return "意图尚未识别，请输入您的需求描述";
        }
        if (!styleSelected) {
            return "请选择您喜欢的设计风格";
        }
        if (!frontendPrototypeExists) {
            return "前端原型正在生成中，请稍候...";
        }
        if (!designConfirmed) {
            return "请查看原型预览并确认设计方案";
        }
        if (!prototypeUrlAccessible) {
            return "原型预览服务暂时不可用，请稍后重试";
        }
        return null;
    }

    /**
     * 获取完成进度百分比
     *
     * @return 0-100的进度值
     */
    public int getProgressPercentage() {
        int completedSteps = 0;
        int totalSteps = 6;

        if (appSpecExists) completedSteps++;
        if (intentClassified) completedSteps++;
        if (styleSelected) completedSteps++;
        if (frontendPrototypeExists) completedSteps++;
        if (prototypeUrlAccessible) completedSteps++;
        if (designConfirmed) completedSteps++;

        return (completedSteps * 100) / totalSteps;
    }
}
