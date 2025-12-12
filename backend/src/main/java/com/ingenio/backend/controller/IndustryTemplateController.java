package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.TemplateMatchRequest;
import com.ingenio.backend.dto.response.TemplateMatchResponse;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.ingenio.backend.service.IndustryTemplateMatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 行业模板库Controller
 *
 * Phase X.4: 行业模板库功能 - API接口层
 *
 * 提供以下核心功能：
 * 1. 智能模板匹配：基于关键词和URL的多维度匹配
 * 2. 模板详情查询：获取单个模板的完整信息
 * 3. 模板列表浏览：分类浏览、排序、分页
 *
 * API路径：/api/v1/templates
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 行业模板库开发)
 */
@Slf4j
@RestController
@RequestMapping("/v1/templates")
@RequiredArgsConstructor
@Tag(name = "行业模板库", description = "V2.0行业模板库API - 提供智能模板匹配和浏览功能")
public class IndustryTemplateController {

    private final IndustryTemplateMatchingService matchingService;
    private final IndustryTemplateMapper templateMapper;

    /**
     * 智能模板匹配
     *
     * 根据用户输入的关键词和参考URL，使用多维度评分算法匹配最合适的行业模板
     *
     * 匹配算法：
     * - Jaccard相似度（关键词匹配）：60%权重
     * - 分类匹配：20%权重（Task 4集成IntentClassifier后启用）
     * - URL相似度：15%权重
     * - 复杂度惩罚：5%权重（负向）
     *
     * 请求示例：
     * <pre>
     * POST /api/v1/templates/match
     * {
     *   "keywords": ["民宿", "预订", "airbnb"],
     *   "referenceUrl": "https://www.airbnb.com",
     *   "topN": 5
     * }
     * </pre>
     *
     * 响应示例：
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": [
     *     {
     *       "template": {
     *         "id": "uuid",
     *         "name": "民宿预订平台模板",
     *         "category": "生活服务",
     *         "subcategory": "住宿预订",
     *         "keywords": ["民宿", "预订", "住宿", "airbnb", "短租"],
     *         "referenceUrl": "https://www.airbnb.com",
     *         "complexityScore": 6,
     *         "usageCount": 128,
     *         "rating": 4.5,
     *         ...
     *       },
     *       "totalScore": 0.82,
     *       "keywordScore": 0.75,
     *       "categoryScore": 0.0,
     *       "urlScore": 1.0,
     *       "complexityPenalty": 0.6
     *     },
     *     ...
     *   ]
     * }
     * </pre>
     *
     * @param request 模板匹配请求（包含关键词、参考URL、topN）
     * @return 匹配结果列表（按总分降序排序）
     */
    @PostMapping("/match")
    @Operation(
        summary = "智能模板匹配",
        description = "基于关键词和URL的多维度模板匹配算法，返回Top N个最匹配的行业模板"
    )
    public Result<List<TemplateMatchResponse>> matchTemplates(
            @Valid @RequestBody TemplateMatchRequest request
    ) {
        try {
            log.info("收到模板匹配请求: keywords={}, referenceUrl={}, topN={}",
                    request.getKeywords(), request.getReferenceUrl(), request.getTopN());

            // 调用匹配服务
            List<IndustryTemplateMatchingService.TemplateMatchResult> matchResults =
                    matchingService.matchTemplates(
                            request.getKeywords(),
                            request.getReferenceUrl(),
                            request.getTopN()
                    );

            // 转换为DTO
            List<TemplateMatchResponse> responses =
                    TemplateMatchResponse.fromServiceResults(matchResults);

            log.info("模板匹配完成，返回 {} 个结果", responses.size());

            return Result.success(responses);

        } catch (Exception e) {
            log.error("模板匹配失败: keywords={}, error={}",
                    request.getKeywords(), e.getMessage(), e);
            return Result.error(500, "模板匹配失败: " + e.getMessage());
        }
    }

    /**
     * 获取模板详情
     *
     * 根据模板ID查询完整的模板信息，包括：
     * - 基础信息（名称、描述、分类）
     * - 关键词列表
     * - 预定义实体和功能
     * - 业务流程定义
     * - 技术栈建议
     * - 使用统计
     *
     * 请求示例：
     * <pre>
     * GET /api/v1/templates/{id}
     * </pre>
     *
     * 响应示例：
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "id": "uuid",
     *     "name": "民宿预订平台模板",
     *     "description": "适用于民宿预订业务的完整模板...",
     *     "category": "生活服务",
     *     "subcategory": "住宿预订",
     *     "keywords": ["民宿", "预订", "airbnb"],
     *     "entities": [
     *       {
     *         "name": "User",
     *         "description": "用户实体",
     *         "attributes": [...]
     *       }
     *     ],
     *     "features": ["用户注册登录", "房源浏览搜索", "在线预订支付"],
     *     "workflows": [...],
     *     "techStack": {
     *       "frontend": ["React", "Next.js"],
     *       "backend": ["Spring Boot", "PostgreSQL"]
     *     },
     *     "complexityScore": 6,
     *     "usageCount": 128,
     *     "rating": 4.5
     *   }
     * }
     * </pre>
     *
     * @param id 模板UUID
     * @return 模板完整信息
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "获取模板详情",
        description = "根据模板ID查询完整的模板信息"
    )
    public Result<IndustryTemplateEntity> getTemplateById(
            @Parameter(description = "模板UUID", required = true)
            @PathVariable UUID id
    ) {
        try {
            log.info("查询模板详情: id={}", id);

            IndustryTemplateEntity template = templateMapper.selectById(id);

            if (template == null) {
                log.warn("模板不存在: id={}", id);
                return Result.error(404, "模板不存在");
            }

            if (!template.getIsActive()) {
                log.warn("模板已禁用: id={}", id);
                return Result.error(403, "模板已禁用");
            }

            log.info("模板详情查询成功: id={}, name={}", id, template.getName());
            return Result.success(template);

        } catch (Exception e) {
            log.error("查询模板详情失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(500, "查询模板详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取模板列表
     *
     * 查询所有启用的模板，支持：
     * - 分类筛选（一级分类、二级分类）
     * - 排序（按使用次数、评分、创建时间）
     * - 分页（限制返回数量）
     *
     * 请求示例：
     * <pre>
     * # 获取所有模板（默认按使用次数降序）
     * GET /api/v1/templates
     *
     * # 按分类筛选
     * GET /api/v1/templates?category=生活服务
     *
     * # 按二级分类筛选
     * GET /api/v1/templates?category=生活服务&subcategory=住宿预订
     *
     * # 按评分排序并限制数量
     * GET /api/v1/templates?sortBy=rating&limit=10
     * </pre>
     *
     * 响应示例：
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": [
     *     {
     *       "id": "uuid",
     *       "name": "民宿预订平台模板",
     *       "category": "生活服务",
     *       "usageCount": 128,
     *       "rating": 4.5,
     *       ...
     *     },
     *     ...
     *   ]
     * }
     * </pre>
     *
     * @param category 一级分类（可选）
     * @param subcategory 二级分类（可选，需要同时指定category）
     * @param sortBy 排序字段（usageCount/rating/createdAt，默认usageCount）
     * @param limit 限制返回数量（默认50，最大100）
     * @return 模板列表
     */
    @GetMapping
    @Operation(
        summary = "获取模板列表",
        description = "查询模板列表，支持分类筛选、排序和分页"
    )
    public Result<List<IndustryTemplateEntity>> listTemplates(
            @Parameter(description = "一级分类（电商/教育/社交/生活服务等）")
            @RequestParam(required = false) String category,

            @Parameter(description = "二级分类")
            @RequestParam(required = false) String subcategory,

            @Parameter(description = "排序字段（usageCount/rating/createdAt）")
            @RequestParam(defaultValue = "usageCount") String sortBy,

            @Parameter(description = "限制返回数量（1-100）")
            @RequestParam(defaultValue = "50") Integer limit
    ) {
        try {
            log.info("查询模板列表: category={}, subcategory={}, sortBy={}, limit={}",
                    category, subcategory, sortBy, limit);

            // 限制limit范围
            if (limit < 1) limit = 1;
            if (limit > 100) limit = 100;

            // 构建查询条件
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IndustryTemplateEntity> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

            // 只查询启用的模板
            wrapper.eq(IndustryTemplateEntity::getIsActive, true);

            // 分类筛选
            if (category != null && !category.trim().isEmpty()) {
                wrapper.eq(IndustryTemplateEntity::getCategory, category);
            }
            if (subcategory != null && !subcategory.trim().isEmpty()) {
                wrapper.eq(IndustryTemplateEntity::getSubcategory, subcategory);
            }

            // 排序
            switch (sortBy.toLowerCase()) {
                case "rating":
                    wrapper.orderByDesc(IndustryTemplateEntity::getRating);
                    break;
                case "createdat":
                case "created_at":
                    wrapper.orderByDesc(IndustryTemplateEntity::getCreatedAt);
                    break;
                case "usagecount":
                case "usage_count":
                default:
                    wrapper.orderByDesc(IndustryTemplateEntity::getUsageCount);
                    break;
            }

            // 限制数量
            wrapper.last("LIMIT " + limit);

            // 执行查询
            List<IndustryTemplateEntity> templates = templateMapper.selectList(wrapper);

            log.info("模板列表查询成功，返回 {} 个结果", templates.size());

            return Result.success(templates);

        } catch (Exception e) {
            log.error("查询模板列表失败: error={}", e.getMessage(), e);
            return Result.error(500, "查询模板列表失败: " + e.getMessage());
        }
    }
}
