package com.ingenio.backend.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.dto.request.admin.PromptSyncRequest;
import com.ingenio.backend.entity.MagicPromptEntity;
import com.ingenio.backend.mapper.MagicPromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API - Prompt同步服务
 *
 * 用途：处理 JeecgBoot 推送的 Prompt 同步请求
 *
 * 核心功能：
 * 1. Prompt 发布（新增或更新）
 * 2. Prompt 下架
 * 3. Prompt 内容更新
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPromptService {

    private final MagicPromptMapper promptMapper;
    private final AdminAuditLogService auditLogService;

    /**
     * 同步 Prompt
     *
     * 根据 action 类型执行不同操作：
     * - publish: 发布（新增或更新）
     * - unpublish: 下架
     * - update: 更新内容
     *
     * @param request 同步请求
     * @return 同步后的 Prompt 实体
     */
    @Transactional
    public MagicPromptEntity syncPrompt(PromptSyncRequest request) {
        String action = request.getAction().toLowerCase();
        log.info("同步 Prompt: action={}, title={}", action, request.getPrompt().getTitle());

        return switch (action) {
            case "publish" -> publishPrompt(request);
            case "unpublish" -> unpublishPrompt(request);
            case "update" -> updatePrompt(request);
            default -> throw new IllegalArgumentException("不支持的同步动作: " + action);
        };
    }

    /**
     * 发布 Prompt（新增或更新）
     */
    private MagicPromptEntity publishPrompt(PromptSyncRequest request) {
        PromptSyncRequest.PromptData data = request.getPrompt();
        UUID promptId = data.getId();
        MagicPromptEntity existingPrompt = null;

        if (promptId != null) {
            existingPrompt = promptMapper.selectById(promptId);
        }

        if (existingPrompt != null) {
            // 更新已有 Prompt
            log.info("更新已存在的 Prompt: id={}", promptId);
            return updateExistingPrompt(existingPrompt, data, request.getOperator());
        } else {
            // 新增 Prompt
            log.info("新增 Prompt: title={}", data.getTitle());
            return createNewPrompt(data, request.getOperator());
        }
    }

    /**
     * 下架 Prompt
     */
    private MagicPromptEntity unpublishPrompt(PromptSyncRequest request) {
        UUID promptId = request.getPrompt().getId();
        if (promptId == null) {
            throw new IllegalArgumentException("下架操作需要提供 Prompt ID");
        }

        MagicPromptEntity prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt 不存在: " + promptId);
        }

        String beforeStatus = prompt.getStatus();
        prompt.setStatus("archived");
        prompt.setUpdatedAt(Instant.now());
        promptMapper.updateById(prompt);

        log.info("下架 Prompt: id={}, beforeStatus={}", promptId, beforeStatus);

        // 记录审计日志
        auditLogService.log(
                request.getTenantId(),
                null,
                request.getOperator(),
                "ADMIN",
                "PROMPT_UNPUBLISH",
                "PROMPT",
                promptId.toString(),
                Map.of("status", beforeStatus),
                Map.of("status", "archived"),
                "SUCCESS",
                null,
                null, null, null, null, null
        );

        return prompt;
    }

    /**
     * 更新 Prompt 内容
     */
    private MagicPromptEntity updatePrompt(PromptSyncRequest request) {
        UUID promptId = request.getPrompt().getId();
        if (promptId == null) {
            throw new IllegalArgumentException("更新操作需要提供 Prompt ID");
        }

        MagicPromptEntity prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt 不存在: " + promptId);
        }

        return updateExistingPrompt(prompt, request.getPrompt(), request.getOperator());
    }

    /**
     * 创建新 Prompt
     */
    private MagicPromptEntity createNewPrompt(PromptSyncRequest.PromptData data, String operator) {
        MagicPromptEntity prompt = MagicPromptEntity.builder()
                .id(data.getId() != null ? data.getId() : UUID.randomUUID())
                .title(data.getTitle())
                .description(data.getDescription())
                .coverImageUrl(data.getCoverImageUrl())
                .promptTemplate(data.getPromptTemplate())
                .defaultVariables(data.getDefaultVariables())
                .ageGroup(data.getAgeGroup())
                .category(data.getCategory())
                .difficultyLevel(data.getDifficultyLevel())
                .exampleProjectId(data.getExampleProjectId())
                .tags(data.getTags())
                .metadata(data.getMetadata())
                .createdByUserId(data.getCreatedByUserId())
                .usageCount(0)
                .likeCount(0)
                .status("active")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        promptMapper.insert(prompt);
        log.info("新增 Prompt 成功: id={}, title={}", prompt.getId(), prompt.getTitle());

        // 记录审计日志
        auditLogService.log(
                null,
                null,
                operator,
                "ADMIN",
                "PROMPT_CREATE",
                "PROMPT",
                prompt.getId().toString(),
                null,
                Map.of("title", prompt.getTitle(), "status", "active"),
                "SUCCESS",
                null,
                null, null, null, null, null
        );

        return prompt;
    }

    /**
     * 更新已有 Prompt
     */
    private MagicPromptEntity updateExistingPrompt(
            MagicPromptEntity prompt,
            PromptSyncRequest.PromptData data,
            String operator
    ) {
        Map<String, Object> beforeState = Map.of(
                "title", prompt.getTitle() != null ? prompt.getTitle() : "",
                "status", prompt.getStatus() != null ? prompt.getStatus() : ""
        );

        // 更新字段
        if (StringUtils.hasText(data.getTitle())) {
            prompt.setTitle(data.getTitle());
        }
        if (data.getDescription() != null) {
            prompt.setDescription(data.getDescription());
        }
        if (data.getCoverImageUrl() != null) {
            prompt.setCoverImageUrl(data.getCoverImageUrl());
        }
        if (StringUtils.hasText(data.getPromptTemplate())) {
            prompt.setPromptTemplate(data.getPromptTemplate());
        }
        if (data.getDefaultVariables() != null) {
            prompt.setDefaultVariables(data.getDefaultVariables());
        }
        if (data.getAgeGroup() != null) {
            prompt.setAgeGroup(data.getAgeGroup());
        }
        if (data.getCategory() != null) {
            prompt.setCategory(data.getCategory());
        }
        if (data.getDifficultyLevel() != null) {
            prompt.setDifficultyLevel(data.getDifficultyLevel());
        }
        if (data.getTags() != null) {
            prompt.setTags(data.getTags());
        }
        if (data.getMetadata() != null) {
            prompt.setMetadata(data.getMetadata());
        }

        // 发布时确保状态为 active
        prompt.setStatus("active");
        prompt.setUpdatedAt(Instant.now());

        promptMapper.updateById(prompt);
        log.info("更新 Prompt 成功: id={}, title={}", prompt.getId(), prompt.getTitle());

        // 记录审计日志
        auditLogService.log(
                null,
                null,
                operator,
                "ADMIN",
                "PROMPT_UPDATE",
                "PROMPT",
                prompt.getId().toString(),
                beforeState,
                Map.of("title", prompt.getTitle(), "status", prompt.getStatus()),
                "SUCCESS",
                null,
                null, null, null, null, null
        );

        return prompt;
    }

    /**
     * 分页查询 Prompt 列表
     *
     * @param status     状态（可选）
     * @param category   分类（可选）
     * @param ageGroup   年龄分组（可选）
     * @param keyword    搜索关键词（可选）
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 分页结果
     */
    public Page<MagicPromptEntity> listPrompts(
            String status,
            String category,
            String ageGroup,
            String keyword,
            int pageNum,
            int pageSize
    ) {
        LambdaQueryWrapper<MagicPromptEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            wrapper.eq(MagicPromptEntity::getStatus, status);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(MagicPromptEntity::getCategory, category);
        }
        if (StringUtils.hasText(ageGroup)) {
            wrapper.eq(MagicPromptEntity::getAgeGroup, ageGroup);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(MagicPromptEntity::getTitle, keyword)
                    .or()
                    .like(MagicPromptEntity::getDescription, keyword)
            );
        }

        wrapper.orderByDesc(MagicPromptEntity::getUpdatedAt);

        Page<MagicPromptEntity> page = new Page<>(pageNum, pageSize);
        return promptMapper.selectPage(page, wrapper);
    }

    /**
     * 获取 Prompt 详情
     *
     * @param promptId Prompt ID
     * @return Prompt 实体
     */
    public MagicPromptEntity getPrompt(UUID promptId) {
        return promptMapper.selectById(promptId);
    }

    /**
     * 硬删除 Prompt
     *
     * @param promptId Prompt ID
     * @param operator 操作人
     */
    @Transactional
    public void deletePrompt(UUID promptId, String operator) {
        MagicPromptEntity prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt 不存在: " + promptId);
        }

        promptMapper.deleteById(promptId);
        log.info("删除 Prompt: id={}, title={}", promptId, prompt.getTitle());

        // 记录审计日志
        auditLogService.log(
                null,
                null,
                operator,
                "ADMIN",
                "PROMPT_DELETE",
                "PROMPT",
                promptId.toString(),
                Map.of("title", prompt.getTitle()),
                null,
                "SUCCESS",
                null,
                null, null, null, null, null
        );
    }

    /**
     * 统计 Prompt 数量
     *
     * @param status   状态（可选）
     * @param category 分类（可选）
     * @return Prompt 数量
     */
    public long countPrompts(String status, String category) {
        LambdaQueryWrapper<MagicPromptEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(status)) {
            wrapper.eq(MagicPromptEntity::getStatus, status);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(MagicPromptEntity::getCategory, category);
        }

        return promptMapper.selectCount(wrapper);
    }
}
