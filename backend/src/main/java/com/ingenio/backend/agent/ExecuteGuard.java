package com.ingenio.backend.agent;

import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * ExecuteGuard - Execute阶段前置条件检查守卫
 *
 * V2.0核心组件，负责在ExecuteAgent执行前验证所有前置条件：
 * 1. AppSpec存在性检查
 * 2. 用户设计确认状态检查（关键阻塞点）
 * 3. 前端原型代码完整性检查
 * 4. 原型预览URL可访问性检查
 * 5. 设计风格选择检查
 * 6. 意图识别完成检查
 *
 * 设计原则：
 * - 用户必须先确认设计方案，才能进入Execute阶段（降低50%返工率）
 * - 所有检查失败都会抛出明确的BusinessException
 * - 提供详细的错误信息帮助用户理解阻塞原因
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteGuard {

    private final AppSpecMapper appSpecMapper;

    /**
     * URL可访问性检查器。
     *
     * 是什么：用于检测原型预览URL可达性的组件。
     * 做什么：统一封装可达性检测逻辑，供执行前置检查复用。
     * 为什么：保留严格检测的同时，便于测试环境替换实现。
     */
    private final UrlAccessibilityChecker urlAccessibilityChecker;

    /**
     * 执行完整的前置条件检查
     *
     * 检查顺序（按重要性排序）：
     * 1. AppSpec存在性 - 基础检查
     * 2. 意图识别完成 - Plan阶段产出
     * 3. 设计风格选择 - 用户决策
     * 4. 前端原型代码 - OpenLovable产出
     * 5. 用户设计确认 - 核心阻塞点
     * 6. 原型预览URL - Sandbox部署
     *
     * @param appSpecId AppSpec的UUID
     * @throws BusinessException 如果任何前置条件不满足
     */
    public void checkPreconditions(UUID appSpecId) {
        log.info("ExecuteGuard开始前置条件检查 - appSpecId: {}", appSpecId);

        // Step 1: 检查AppSpec是否存在
        AppSpecEntity appSpec = checkAppSpecExists(appSpecId);

        // Step 2: 检查意图识别是否完成
        checkIntentClassified(appSpec);

        // Step 3: 检查设计风格是否选择
        checkStyleSelected(appSpec);

        // Step 4: 检查前端原型代码是否存在
        checkFrontendPrototypeExists(appSpec);

        // Step 5: 核心检查 - 用户是否确认设计
        checkDesignConfirmed(appSpec);

        // Step 6: 检查原型预览URL是否有效
        checkPrototypeUrlAccessible(appSpec);

        log.info("ExecuteGuard前置条件检查全部通过 - appSpecId: {}", appSpecId);
    }

    /**
     * 检查AppSpec是否存在
     *
     * @param appSpecId AppSpec的UUID
     * @return 存在的AppSpecEntity
     * @throws BusinessException 如果AppSpec不存在
     */
    private AppSpecEntity checkAppSpecExists(UUID appSpecId) {
        log.debug("检查AppSpec存在性 - appSpecId: {}", appSpecId);

        AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
        if (appSpec == null) {
            log.error("AppSpec不存在 - appSpecId: {}", appSpecId);
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_APPSPEC_NOT_FOUND,
                appSpecId.toString()
            );
        }

        log.debug("AppSpec存在性检查通过 - appSpecId: {}", appSpecId);
        return appSpec;
    }

    /**
     * 检查意图识别是否完成
     *
     * @param appSpec AppSpec实体
     * @throws BusinessException 如果意图未识别
     */
    private void checkIntentClassified(AppSpecEntity appSpec) {
        log.debug("检查意图识别完成状态 - appSpecId: {}", appSpec.getId());

        if (!StringUtils.hasText(appSpec.getIntentType())) {
            log.error("意图尚未识别 - appSpecId: {}", appSpec.getId());
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_INTENT_NOT_CLASSIFIED,
                appSpec.getId().toString()
            );
        }

        log.debug("意图识别检查通过 - intentType: {}", appSpec.getIntentType());
    }

    /**
     * 检查设计风格是否已选择
     *
     * @param appSpec AppSpec实体
     * @throws BusinessException 如果未选择风格
     */
    private void checkStyleSelected(AppSpecEntity appSpec) {
        log.debug("检查设计风格选择状态 - appSpecId: {}", appSpec.getId());

        if (!StringUtils.hasText(appSpec.getSelectedStyle())) {
            log.error("未选择设计风格 - appSpecId: {}", appSpec.getId());
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_NO_SELECTED_STYLE,
                appSpec.getId().toString()
            );
        }

        log.debug("设计风格检查通过 - selectedStyle: {}", appSpec.getSelectedStyle());
    }

    /**
     * 检查前端原型代码是否存在
     *
     * @param appSpec AppSpec实体
     * @throws BusinessException 如果原型代码缺失
     */
    private void checkFrontendPrototypeExists(AppSpecEntity appSpec) {
        log.debug("检查前端原型代码存在性 - appSpecId: {}", appSpec.getId());

        if (appSpec.getFrontendPrototype() == null || appSpec.getFrontendPrototype().isEmpty()) {
            log.error("前端原型代码缺失 - appSpecId: {}", appSpec.getId());
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_NO_FRONTEND_PROTOTYPE,
                appSpec.getId().toString()
            );
        }

        log.debug("前端原型代码检查通过 - prototypeSize: {} keys", appSpec.getFrontendPrototype().size());
    }

    /**
     * 检查原型预览URL是否可访问
     *
     * @param appSpec AppSpec实体
     * @throws BusinessException 如果URL无效或不可访问
     */
    private void checkPrototypeUrlAccessible(AppSpecEntity appSpec) {
        log.debug("检查原型预览URL可访问性 - appSpecId: {}", appSpec.getId());

        String prototypeUrl = appSpec.getFrontendPrototypeUrl();
        if (!StringUtils.hasText(prototypeUrl)) {
            log.error("原型预览URL缺失 - appSpecId: {}", appSpec.getId());
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID,
                appSpec.getId().toString()
            );
        }

        // 验证URL格式和可访问性
        if (!isUrlAccessible(prototypeUrl)) {
            log.error("原型预览URL不可访问 - url: {}", prototypeUrl);
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID,
                prototypeUrl
            );
        }

        log.debug("原型预览URL检查通过 - url: {}", prototypeUrl);
    }

    /**
     * 核心检查：用户是否已确认设计方案
     *
     * V2.0架构的关键阻塞点：
     * - 用户必须在前端点击"确认设计"按钮
     * - 确认后才能进入Execute阶段生成后端代码
     * - 这是降低返工率50%的核心机制
     *
     * @param appSpec AppSpec实体
     * @throws BusinessException 如果用户未确认设计
     */
    private void checkDesignConfirmed(AppSpecEntity appSpec) {
        log.debug("检查用户设计确认状态 - appSpecId: {}", appSpec.getId());

        Boolean designConfirmed = appSpec.getDesignConfirmed();
        if (designConfirmed == null || !designConfirmed) {
            log.warn("用户尚未确认设计方案，Execute阶段被阻塞 - appSpecId: {}", appSpec.getId());
            throw new BusinessException(
                ErrorCode.EXECUTE_GUARD_PROTOTYPE_NOT_CONFIRMED,
                appSpec.getId().toString()
            );
        }

        log.info("用户设计确认检查通过 - confirmedAt: {}", appSpec.getDesignConfirmedAt());
    }

    /**
     * 检查URL是否可访问
     *
     * 委托可达性检查器执行真实检测
     *
     * @param urlString URL字符串
     * @return true如果可访问，false如果不可访问
     */
    private boolean isUrlAccessible(String urlString) {
        return urlAccessibilityChecker.isAccessible(urlString);
    }

    /**
     * 获取前置条件检查报告
     *
     * 返回所有检查项的当前状态，用于前端展示
     *
     * @param appSpecId AppSpec的UUID
     * @return 检查报告对象
     */
    public ExecuteGuardReport getCheckReport(UUID appSpecId) {
        log.info("生成ExecuteGuard检查报告 - appSpecId: {}", appSpecId);

        AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
        if (appSpec == null) {
            return ExecuteGuardReport.builder()
                .appSpecExists(false)
                .intentClassified(false)
                .styleSelected(false)
                .frontendPrototypeExists(false)
                .prototypeUrlAccessible(false)
                .designConfirmed(false)
                .allChecksPassed(false)
                .build();
        }

        boolean intentClassified = StringUtils.hasText(appSpec.getIntentType());
        boolean styleSelected = StringUtils.hasText(appSpec.getSelectedStyle());
        boolean frontendPrototypeExists = appSpec.getFrontendPrototype() != null && !appSpec.getFrontendPrototype().isEmpty();
        boolean prototypeUrlAccessible = StringUtils.hasText(appSpec.getFrontendPrototypeUrl()) &&
                                         isUrlAccessible(appSpec.getFrontendPrototypeUrl());
        boolean designConfirmed = Boolean.TRUE.equals(appSpec.getDesignConfirmed());

        boolean allPassed = intentClassified && styleSelected && frontendPrototypeExists &&
                           prototypeUrlAccessible && designConfirmed;

        return ExecuteGuardReport.builder()
            .appSpecExists(true)
            .intentClassified(intentClassified)
            .styleSelected(styleSelected)
            .frontendPrototypeExists(frontendPrototypeExists)
            .prototypeUrlAccessible(prototypeUrlAccessible)
            .designConfirmed(designConfirmed)
            .allChecksPassed(allPassed)
            .intentType(appSpec.getIntentType())
            .selectedStyle(appSpec.getSelectedStyle())
            .prototypeUrl(appSpec.getFrontendPrototypeUrl())
            .designConfirmedAt(appSpec.getDesignConfirmedAt())
            .build();
    }
}
