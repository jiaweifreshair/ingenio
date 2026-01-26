package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.dto.response.AnalysisProgressMessage;
import com.ingenio.backend.entity.InteractiveAnalysisSessionEntity;
import com.ingenio.backend.mapper.InteractiveAnalysisSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 交互式分析服务
 *
 * 实现AI深度思考的交互式分析流程:
 * - 每个步骤完成后等待人工确认
 * - 用户可以提出修改建议后重新执行当前步骤
 * - 完整的会话状态跟踪
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveAnalysisService {

    private final InteractiveAnalysisSessionMapper sessionMapper;
    private final NLRequirementAnalyzer requirementAnalyzer;

    /**
     * 启动交互式分析会话
     *
     * @param userId      用户ID
     * @param requirement 需求描述
     * @return 会话ID
     */
    @Transactional
    public String startSession(Long userId, String requirement) {
        log.info("启动交互式分析会话: userId={}, requirement={}", userId, requirement);

        InteractiveAnalysisSessionEntity session = new InteractiveAnalysisSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setRequirement(requirement);
        session.setCurrentStep(1);
        session.setStatus("RUNNING");
        session.setStepResults(new HashMap<>());
        session.setStepFeedback(new HashMap<>());
        session.setStepRetries(new HashMap<>());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(session);

        log.info("交互式分析会话已创建: sessionId={}", session.getSessionId());
        return session.getSessionId();
    }

    /**
     * 执行当前步骤的分析
     *
     * @param sessionId        会话ID
     * @param progressCallback 进度回调
     * @return 步骤执行结果
     */
    @Transactional
    public Object executeCurrentStep(String sessionId, Consumer<AnalysisProgressMessage> progressCallback) {
        InteractiveAnalysisSessionEntity session = getSession(sessionId);

        if (!"RUNNING".equals(session.getStatus())) {
            throw new IllegalStateException("会话状态不是RUNNING,无法执行步骤: " + session.getStatus());
        }

        int currentStep = session.getCurrentStep();
        log.info("执行步骤 {}: sessionId={}", currentStep, sessionId);

        try {
            // 获取当前步骤的用户反馈(如果有)
            String currentFeedback = session.getStepFeedback().get(currentStep);

            // 执行单步分析,传递完整上下文
            Object stepResult = requirementAnalyzer.analyzeSingleStep(
                    session.getRequirement(),
                    currentStep,
                    session.getStepResults(), // 传递历史结果
                    session.getStepFeedback(), // 传递历史反馈
                    currentFeedback, // 传递当前反馈
                    progressCallback);

            // 保存步骤结果
            session.getStepResults().put(currentStep, stepResult);
            session.setStatus("WAITING_CONFIRMATION");
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);

            log.info("步骤 {} 执行完成,等待用户确认: sessionId={}", currentStep, sessionId);
            return stepResult;

        } catch (Exception e) {
            log.error("步骤 {} 执行失败: sessionId={}", currentStep, sessionId, e);
            session.setStatus("FAILED");
            session.setErrorMessage(e.getMessage());
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            throw new RuntimeException("步骤执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 确认当前步骤,进入下一步
     *
     * @param sessionId 会话ID
     * @param step      确认的步骤编号
     */
    @Transactional
    public void confirmStep(String sessionId, int step) {
        // 重试机制：最多等待3秒，每500ms检查一次
        // 解决前端收到COMPLETED消息后立即发送确认请求，但后端事务还未提交的竞态问题
        int maxRetries = 6;
        int retryInterval = 500; // 毫秒

        InteractiveAnalysisSessionEntity session = null;
        boolean canConfirm = false;

        for (int i = 0; i < maxRetries; i++) {
            session = getSession(sessionId);

            // 容错处理：如果状态是RUNNING但步骤已有结果，说明SSE流刚完成还未完全同步状态，允许确认
            canConfirm = "WAITING_CONFIRMATION".equals(session.getStatus()) ||
                    ("RUNNING".equals(session.getStatus()) && session.getStepResults().containsKey(step));

            if (canConfirm) {
                break;
            }

            // 如果是最后一次重试，不再等待
            if (i < maxRetries - 1) {
                log.debug("等待步骤完成: sessionId={}, 当前状态={}, 重试次数={}/{}",
                        sessionId, session.getStatus(), i + 1, maxRetries);
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!canConfirm) {
            log.error("确认步骤失败: sessionId={}, 当前状态={}, 期望状态=WAITING_CONFIRMATION或RUNNING(有结果)",
                    sessionId, session.getStatus());
            throw new IllegalStateException("会话状态不允许确认步骤: " + session.getStatus() +
                    "，请等待当前步骤执行完成");
        }

        if (session.getCurrentStep() != step) {
            log.error("确认步骤失败: sessionId={}, 步骤号不匹配, 期望={}, 实际={}",
                    sessionId, session.getCurrentStep(), step);
            throw new IllegalArgumentException("步骤编号不匹配: expected=" + session.getCurrentStep() + ", actual=" + step);
        }

        log.info("用户确认步骤 {}: sessionId={}, 当前状态={}", step, sessionId, session.getStatus());

        // 如果是最后一步,标记为完成
        if (step == 6) {
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            // 保存最终结果
            session.setFinalResult(session.getStepResults().get(6));

            // M1: 提取并保存 Blueprint Markdown，用于透传给 OpenLovable/G3
            Object step6Result = session.getStepResults().get(6);
            if (step6Result instanceof Map<?, ?> resultMap) {
                Object blueprint = resultMap.get("blueprint");
                if (blueprint instanceof String blueprintMarkdown && !blueprintMarkdown.isBlank()) {
                    session.setBlueprintMarkdown(blueprintMarkdown);
                    log.info("Blueprint Markdown 已保存: sessionId={}, 长度={}", sessionId, blueprintMarkdown.length());
                }
            }

            log.info("所有步骤完成: sessionId={}", sessionId);
        } else {
            // 进入下一步
            session.setCurrentStep(step + 1);
            session.setStatus("RUNNING");
            log.info("进入步骤 {}: sessionId={}", step + 1, sessionId);
        }

        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 提出修改建议,重新执行当前步骤
     *
     * @param sessionId 会话ID
     * @param step      步骤编号
     * @param feedback  用户反馈
     */
    @Transactional
    public void modifyStep(String sessionId, int step, String feedback) {
        // 重试机制：最多等待3秒，每500ms检查一次
        // 解决前端收到COMPLETED消息后立即发送修改请求，但后端事务还未提交的竞态问题
        int maxRetries = 6;
        int retryInterval = 500; // 毫秒

        InteractiveAnalysisSessionEntity session = null;
        boolean canModify = false;

        for (int i = 0; i < maxRetries; i++) {
            session = getSession(sessionId);

            // 容错处理：如果状态是RUNNING但步骤已有结果，说明SSE流刚完成还未完全同步状态，允许修改
            canModify = "WAITING_CONFIRMATION".equals(session.getStatus()) ||
                    ("RUNNING".equals(session.getStatus()) && session.getStepResults().containsKey(step));

            if (canModify) {
                break;
            }

            // 如果是最后一次重试，不再等待
            if (i < maxRetries - 1) {
                log.debug("等待步骤完成: sessionId={}, 当前状态={}, 重试次数={}/{}",
                        sessionId, session.getStatus(), i + 1, maxRetries);
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!canModify) {
            log.error("修改步骤失败: sessionId={}, 当前状态={}, 期望状态=WAITING_CONFIRMATION或RUNNING(有结果)",
                    sessionId, session.getStatus());
            throw new IllegalStateException("会话状态不允许修改步骤: " + session.getStatus() +
                    "，请等待当前步骤执行完成");
        }

        if (session.getCurrentStep() != step) {
            log.error("修改步骤失败: sessionId={}, 步骤号不匹配, 期望={}, 实际={}",
                    sessionId, session.getCurrentStep(), step);
            throw new IllegalArgumentException("步骤编号不匹配: expected=" + session.getCurrentStep() + ", actual=" + step);
        }

        log.info("用户提出修改建议,重新执行步骤 {}: sessionId={}, 当前状态={}, feedback={}",
                step, sessionId, session.getStatus(), feedback);

        // 保存用户反馈
        session.getStepFeedback().put(step, feedback);

        // 增加重试次数
        Map<Integer, Integer> retries = session.getStepRetries();
        retries.put(step, retries.getOrDefault(step, 0) + 1);

        // 标记为重新运行
        session.setStatus("RUNNING");
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 取消会话
     *
     * @param sessionId 会话ID
     */
    @Transactional
    public void cancelSession(String sessionId) {
        InteractiveAnalysisSessionEntity session = getSession(sessionId);

        log.info("取消会话: sessionId={}", sessionId);

        session.setStatus("CANCELLED");
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话实体
     */
    public InteractiveAnalysisSessionEntity getSession(String sessionId) {
        InteractiveAnalysisSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }
        return session;
    }

    /**
     * 获取用户的所有会话
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public java.util.List<InteractiveAnalysisSessionEntity> getUserSessions(Long userId) {
        LambdaQueryWrapper<InteractiveAnalysisSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InteractiveAnalysisSessionEntity::getUserId, userId)
                .orderByDesc(InteractiveAnalysisSessionEntity::getCreatedAt);
        return sessionMapper.selectList(wrapper);
    }
}
