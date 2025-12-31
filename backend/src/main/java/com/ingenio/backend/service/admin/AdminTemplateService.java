package com.ingenio.backend.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.dto.request.admin.TemplateSyncRequest;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin API - 模板管理服务
 *
 * 用途：处理 JeecgBoot 推送的模板同步请求
 *
 * 核心功能：
 * 1. 模板发布（publish）：创建或更新模板
 * 2. 模板下线（unpublish）：软删除模板
 * 3. 模板更新（update）：仅更新模板内容
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTemplateService {

    private final IndustryTemplateMapper templateMapper;

    /**
     * 同步单个模板
     *
     * @param request 同步请求
     * @return 同步后的模板实体
     */
    @Transactional
    public IndustryTemplateEntity syncTemplate(TemplateSyncRequest request) {
        String action = request.getAction().toLowerCase();
        TemplateSyncRequest.TemplateData templateData = request.getTemplate();

        switch (action) {
            case "publish":
                return publishTemplate(templateData, request.getOperator());
            case "unpublish":
                return unpublishTemplate(templateData.getId(), request.getOperator());
            case "update":
                return updateTemplate(templateData, request.getOperator());
            default:
                throw new IllegalArgumentException("不支持的同步动作: " + action);
        }
    }

    /**
     * 批量同步模板
     *
     * @param requests 批量同步请求
     * @return 同步后的模板列表
     */
    @Transactional
    public List<IndustryTemplateEntity> syncTemplatesBatch(List<TemplateSyncRequest> requests) {
        List<IndustryTemplateEntity> results = new ArrayList<>();
        for (TemplateSyncRequest request : requests) {
            try {
                IndustryTemplateEntity result = syncTemplate(request);
                results.add(result);
            } catch (Exception e) {
                log.error("批量同步中单个模板失败: templateId={}, error={}",
                        request.getTemplate() != null ? request.getTemplate().getId() : null,
                        e.getMessage());
                // 继续处理其他模板，不中断整个批量操作
            }
        }
        return results;
    }

    /**
     * 发布模板（创建或更新）
     *
     * @param templateData 模板数据
     * @param operator 操作人
     * @return 发布后的模板
     */
    private IndustryTemplateEntity publishTemplate(TemplateSyncRequest.TemplateData templateData, String operator) {
        UUID templateId = templateData.getId();

        // 检查是否已存在
        IndustryTemplateEntity existing = null;
        if (templateId != null) {
            existing = templateMapper.selectById(templateId);
        }

        if (existing != null) {
            // 更新已存在的模板
            log.info("更新已存在的模板: id={}", templateId);
            updateEntityFromData(existing, templateData);
            existing.setIsActive(true);
            existing.setUpdatedAt(Instant.now());
            templateMapper.updateById(existing);
            return existing;
        } else {
            // 创建新模板
            log.info("创建新模板: name={}", templateData.getName());
            IndustryTemplateEntity newTemplate = createEntityFromData(templateData, operator);
            newTemplate.setIsActive(true);
            templateMapper.insert(newTemplate);
            return newTemplate;
        }
    }

    /**
     * 下线模板（软删除）
     *
     * @param templateId 模板ID
     * @param operator 操作人
     * @return 下线后的模板
     */
    private IndustryTemplateEntity unpublishTemplate(UUID templateId, String operator) {
        if (templateId == null) {
            throw new IllegalArgumentException("下线模板时 ID 不能为空");
        }

        IndustryTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }

        log.info("下线模板: id={}, name={}", templateId, template.getName());
        template.setIsActive(false);
        template.setUpdatedAt(Instant.now());
        templateMapper.updateById(template);
        return template;
    }

    /**
     * 更新模板内容（不改变发布状态）
     *
     * @param templateData 模板数据
     * @param operator 操作人
     * @return 更新后的模板
     */
    private IndustryTemplateEntity updateTemplate(TemplateSyncRequest.TemplateData templateData, String operator) {
        UUID templateId = templateData.getId();
        if (templateId == null) {
            throw new IllegalArgumentException("更新模板时 ID 不能为空");
        }

        IndustryTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }

        log.info("更新模板内容: id={}, name={}", templateId, template.getName());
        updateEntityFromData(template, templateData);
        template.setUpdatedAt(Instant.now());
        templateMapper.updateById(template);
        return template;
    }

    /**
     * 从 DTO 创建实体
     */
    private IndustryTemplateEntity createEntityFromData(TemplateSyncRequest.TemplateData data, String operator) {
        IndustryTemplateEntity entity = new IndustryTemplateEntity();

        // 如果提供了 ID 则使用，否则生成新 ID
        if (data.getId() != null) {
            entity.setId(data.getId());
        }

        entity.setName(data.getName());
        entity.setDescription(data.getDescription());
        entity.setCategory(data.getCategory());
        entity.setSubcategory(data.getSubcategory());
        entity.setKeywords(data.getKeywords());
        entity.setReferenceUrl(data.getReferenceUrl());
        entity.setThumbnailUrl(data.getThumbnailUrl());
        entity.setEntities(data.getEntities());
        entity.setFeatures(data.getFeatures());
        entity.setWorkflows(data.getWorkflows());
        entity.setBlueprintSpec(data.getBlueprintSpec());
        entity.setTechStack(data.getTechStack());
        entity.setComplexityScore(data.getComplexityScore() != null ? data.getComplexityScore() : 5);
        entity.setEstimatedHours(data.getEstimatedHours());
        entity.setUsageCount(0);
        entity.setCreatedBy(StringUtils.hasText(operator) ? operator : "JEECG_SYNC");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return entity;
    }

    /**
     * 从 DTO 更新实体
     */
    private void updateEntityFromData(IndustryTemplateEntity entity, TemplateSyncRequest.TemplateData data) {
        if (StringUtils.hasText(data.getName())) {
            entity.setName(data.getName());
        }
        if (data.getDescription() != null) {
            entity.setDescription(data.getDescription());
        }
        if (data.getCategory() != null) {
            entity.setCategory(data.getCategory());
        }
        if (data.getSubcategory() != null) {
            entity.setSubcategory(data.getSubcategory());
        }
        if (data.getKeywords() != null) {
            entity.setKeywords(data.getKeywords());
        }
        if (data.getReferenceUrl() != null) {
            entity.setReferenceUrl(data.getReferenceUrl());
        }
        if (data.getThumbnailUrl() != null) {
            entity.setThumbnailUrl(data.getThumbnailUrl());
        }
        if (data.getEntities() != null) {
            entity.setEntities(data.getEntities());
        }
        if (data.getFeatures() != null) {
            entity.setFeatures(data.getFeatures());
        }
        if (data.getWorkflows() != null) {
            entity.setWorkflows(data.getWorkflows());
        }
        if (data.getBlueprintSpec() != null) {
            entity.setBlueprintSpec(data.getBlueprintSpec());
        }
        if (data.getTechStack() != null) {
            entity.setTechStack(data.getTechStack());
        }
        if (data.getComplexityScore() != null) {
            entity.setComplexityScore(data.getComplexityScore());
        }
        if (data.getEstimatedHours() != null) {
            entity.setEstimatedHours(data.getEstimatedHours());
        }
    }

    /**
     * 查询模板列表
     *
     * @param category 分类筛选（可选）
     * @param isActive 是否启用（可选）
     * @return 模板列表
     */
    public List<IndustryTemplateEntity> listTemplates(String category, Boolean isActive) {
        LambdaQueryWrapper<IndustryTemplateEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(category)) {
            wrapper.eq(IndustryTemplateEntity::getCategory, category);
        }
        if (isActive != null) {
            wrapper.eq(IndustryTemplateEntity::getIsActive, isActive);
        }

        wrapper.orderByDesc(IndustryTemplateEntity::getUpdatedAt);

        return templateMapper.selectList(wrapper);
    }

    /**
     * 查询单个模板
     *
     * @param id 模板ID
     * @return 模板实体
     */
    public IndustryTemplateEntity getTemplate(UUID id) {
        return templateMapper.selectById(id);
    }

    /**
     * 删除模板（硬删除）
     *
     * @param id 模板ID
     */
    @Transactional
    public void deleteTemplate(UUID id) {
        IndustryTemplateEntity template = templateMapper.selectById(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }

        log.warn("硬删除模板: id={}, name={}", id, template.getName());
        templateMapper.deleteById(id);
    }
}
