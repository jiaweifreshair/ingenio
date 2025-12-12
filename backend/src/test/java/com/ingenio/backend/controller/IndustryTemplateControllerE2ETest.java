package com.ingenio.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.TemplateMatchRequest;
import com.ingenio.backend.dto.response.TemplateMatchResponse;
import com.ingenio.backend.e2e.BaseE2ETest;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

/**
 * IndustryTemplateController E2E集成测试
 *
 * Phase X.4: 行业模板库功能 - Controller端到端测试
 *
 * 测试策略:
 * 1. 继承BaseE2ETest，使用真实PostgreSQL数据库（TestContainer）
 * 2. 零Mock策略：所有组件使用真实实现
 * 3. 测试完整的模板管理流程：初始化数据→匹配→查询详情→列表浏览
 *
 * 测试覆盖:
 * - POST /api/v1/templates/match - 智能模板匹配
 * - GET /api/v1/templates/{id} - 获取模板详情
 * - GET /api/v1/templates - 获取模板列表（支持筛选、排序、分页）
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 Task 6)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndustryTemplateControllerE2ETest extends BaseE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IndustryTemplateMapper templateMapper;

    private UUID testTemplateId;

    /**
     * 测试类初始化 - 插入测试模板数据
     *
     * 创建3个测试模板：
     * 1. 民宿预订平台模板 (生活服务 > 住宿预订)
     * 2. 电商平台模板 (电商 > 综合电商)
     * 3. 在线教育平台模板 (教育 > 在线教育)
     */
    @BeforeAll
    public void initTestTemplates() {
        // 清空现有数据
        templateMapper.delete(null);

        // 创建测试模板1: 民宿预订平台
        IndustryTemplateEntity template1 = createTestTemplate(
            "民宿预订平台模板",
            "参考Airbnb的民宿预订平台，支持房源发布、在线预订、评价系统",
            "生活服务",
            "住宿预订",
            Arrays.asList("民宿", "预订", "住宿", "airbnb", "短租"),
            "https://www.airbnb.com",
            6,
            100,
            new BigDecimal("4.5")
        );
        templateMapper.insert(template1);
        testTemplateId = template1.getId(); // 保存ID供后续测试使用

        // 创建测试模板2: 电商平台
        IndustryTemplateEntity template2 = createTestTemplate(
            "综合电商平台模板",
            "参考淘宝的综合电商平台，支持多商家入驻、商品管理、订单支付",
            "电商",
            "综合电商",
            Arrays.asList("电商", "商城", "购物", "淘宝", "订单"),
            "https://www.taobao.com",
            8,
            120,
            new BigDecimal("4.8")
        );
        templateMapper.insert(template2);

        // 创建测试模板3: 在线教育平台
        IndustryTemplateEntity template3 = createTestTemplate(
            "在线教育平台模板",
            "在线教育平台，支持课程发布、视频直播、作业批改",
            "教育",
            "在线教育",
            Arrays.asList("教育", "课程", "学习", "直播", "作业"),
            "https://www.xuetangx.com",
            7,
            80,
            new BigDecimal("4.3")
        );
        templateMapper.insert(template3);
    }

    // ==================== 测试用例 ====================

    /**
     * 测试1: 智能模板匹配 - 成功匹配
     */
    @Test
    @Order(1)
    @DisplayName("E2E测试1: 智能模板匹配 - 成功匹配民宿模板")
    void testMatchTemplates_Success() throws Exception {
        // Arrange
        TemplateMatchRequest request = new TemplateMatchRequest();
        request.setKeywords(Arrays.asList("民宿", "预订", "airbnb"));
        request.setReferenceUrl("https://www.airbnb.com");
        request.setTopN(3);

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/v1/templates/match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证响应数据
        String responseBody = result.getResponse().getContentAsString();
        Result<List<TemplateMatchResponse>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<TemplateMatchResponse>>>() {}
        );

        assertThat(apiResult.getData()).isNotEmpty();
        assertThat(apiResult.getData().size()).isLessThanOrEqualTo(3);

        // 验证第一个匹配结果（应该是民宿模板）
        TemplateMatchResponse topMatch = apiResult.getData().get(0);
        assertThat(topMatch.getTemplate().getName()).contains("民宿");
        assertThat(topMatch.getTotalScore()).isGreaterThan(0);
        assertThat(topMatch.getKeywordScore()).isGreaterThan(0);
    }

    /**
     * 测试2: 智能模板匹配 - 空关键词
     */
    @Test
    @Order(2)
    @DisplayName("E2E测试2: 智能模板匹配 - 空关键词返回验证错误")
    void testMatchTemplates_EmptyKeywords() throws Exception {
        // Arrange
        TemplateMatchRequest request = new TemplateMatchRequest();
        request.setKeywords(Arrays.asList());
        request.setReferenceUrl("https://www.airbnb.com");
        request.setTopN(3);

        // Act & Assert
        mockMvc.perform(post("/v1/templates/match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))  // 参数校验错误码
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andExpect(jsonPath("$.data.keywords").value("关键词列表不能为空"));
    }

    /**
     * 测试3: 智能模板匹配 - 无参考URL
     */
    @Test
    @Order(3)
    @DisplayName("E2E测试3: 智能模板匹配 - 无参考URL")
    void testMatchTemplates_NoUrl() throws Exception {
        // Arrange
        TemplateMatchRequest request = new TemplateMatchRequest();
        request.setKeywords(Arrays.asList("电商", "购物"));
        request.setReferenceUrl(null);
        request.setTopN(3);

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/v1/templates/match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证：即使没有URL，关键词匹配也应该工作
        String responseBody = result.getResponse().getContentAsString();
        Result<List<TemplateMatchResponse>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<TemplateMatchResponse>>>() {}
        );

        assertThat(apiResult.getData()).isNotEmpty();
    }

    /**
     * 测试4: 获取模板详情 - 成功
     */
    @Test
    @Order(4)
    @DisplayName("E2E测试4: 获取模板详情 - 成功")
    void testGetTemplateById_Success() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/v1/templates/" + testTemplateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(testTemplateId.toString()))
                .andExpect(jsonPath("$.data.name").value("民宿预订平台模板"))
                .andExpect(jsonPath("$.data.category").value("生活服务"))
                .andExpect(jsonPath("$.data.subcategory").value("住宿预订"))
                .andExpect(jsonPath("$.data.keywords").isArray())
                .andExpect(jsonPath("$.data.referenceUrl").value("https://www.airbnb.com"))
                .andReturn();

        // 验证完整数据结构
        String responseBody = result.getResponse().getContentAsString();
        Result<IndustryTemplateEntity> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<IndustryTemplateEntity>>() {}
        );

        IndustryTemplateEntity template = apiResult.getData();
        assertThat(template.getKeywords()).contains("民宿", "预订", "airbnb");
        assertThat(template.getComplexityScore()).isEqualTo(6);
        assertThat(template.getUsageCount()).isEqualTo(100);
        assertThat(template.getRating()).isEqualByComparingTo(new BigDecimal("4.5"));
    }

    /**
     * 测试5: 获取模板详情 - 模板不存在
     */
    @Test
    @Order(5)
    @DisplayName("E2E测试5: 获取模板详情 - 模板不存在")
    void testGetTemplateById_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/templates/" + nonExistentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("模板不存在"));
    }

    /**
     * 测试6: 获取模板列表 - 无筛选条件
     */
    @Test
    @Order(6)
    @DisplayName("E2E测试6: 获取模板列表 - 无筛选条件")
    void testListTemplates_NoFilter() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证返回所有3个测试模板
        String responseBody = result.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        assertThat(apiResult.getData()).hasSize(3);
    }

    /**
     * 测试7: 获取模板列表 - 按分类筛选
     */
    @Test
    @Order(7)
    @DisplayName("E2E测试7: 获取模板列表 - 按一级分类筛选")
    void testListTemplates_FilterByCategory() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/templates")
                        .param("category", "生活服务"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证只返回生活服务分类的模板
        String responseBody = result.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        assertThat(apiResult.getData()).hasSize(1);
        assertThat(apiResult.getData().get(0).getCategory()).isEqualTo("生活服务");
        assertThat(apiResult.getData().get(0).getName()).contains("民宿");
    }

    /**
     * 测试8: 获取模板列表 - 按二级分类筛选
     */
    @Test
    @Order(8)
    @DisplayName("E2E测试8: 获取模板列表 - 按二级分类筛选")
    void testListTemplates_FilterBySubcategory() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/templates")
                        .param("category", "生活服务")
                        .param("subcategory", "住宿预订"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证只返回住宿预订子分类的模板
        String responseBody = result.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        assertThat(apiResult.getData()).hasSize(1);
        assertThat(apiResult.getData().get(0).getSubcategory()).isEqualTo("住宿预订");
    }

    /**
     * 测试9: 获取模板列表 - 按评分排序
     */
    @Test
    @Order(9)
    @DisplayName("E2E测试9: 获取模板列表 - 按评分降序排序")
    void testListTemplates_SortByRating() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/templates")
                        .param("sortBy", "rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证按评分降序排列
        String responseBody = result.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        List<IndustryTemplateEntity> templates = apiResult.getData();
        assertThat(templates).hasSize(3);

        // 第一个应该是评分最高的电商模板 (4.8)
        assertThat(templates.get(0).getRating()).isEqualByComparingTo(new BigDecimal("4.8"));
        assertThat(templates.get(0).getName()).contains("电商");

        // 验证降序排列
        for (int i = 0; i < templates.size() - 1; i++) {
            assertThat(templates.get(i).getRating())
                .isGreaterThanOrEqualTo(templates.get(i + 1).getRating());
        }
    }

    /**
     * 测试10: 获取模板列表 - 限制返回数量
     */
    @Test
    @Order(10)
    @DisplayName("E2E测试10: 获取模板列表 - 限制返回数量")
    void testListTemplates_WithLimit() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/templates")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        // 验证只返回2个模板
        String responseBody = result.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult = objectMapper.readValue(
            responseBody,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        assertThat(apiResult.getData()).hasSize(2);
    }

    /**
     * 测试11: 获取模板列表 - limit边界值测试
     */
    @Test
    @Order(11)
    @DisplayName("E2E测试11: 获取模板列表 - limit边界值测试")
    void testListTemplates_LimitBoundary() throws Exception {
        // 测试limit=0，应该被调整为1
        MvcResult result1 = mockMvc.perform(get("/v1/templates")
                        .param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String responseBody1 = result1.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult1 = objectMapper.readValue(
            responseBody1,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        assertThat(apiResult1.getData()).hasSize(1);

        // 测试limit=200，应该被调整为100（最大值）
        MvcResult result2 = mockMvc.perform(get("/v1/templates")
                        .param("limit", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String responseBody2 = result2.getResponse().getContentAsString();
        Result<List<IndustryTemplateEntity>> apiResult2 = objectMapper.readValue(
            responseBody2,
            new TypeReference<Result<List<IndustryTemplateEntity>>>() {}
        );

        // 由于测试数据只有3个，返回3个
        assertThat(apiResult2.getData()).hasSize(3);
    }

    // ==================== Helper Methods ====================

    /**
     * 创建测试模板
     */
    private IndustryTemplateEntity createTestTemplate(
        String name,
        String description,
        String category,
        String subcategory,
        List<String> keywords,
        String referenceUrl,
        int complexityScore,
        int usageCount,
        BigDecimal rating
    ) {
        IndustryTemplateEntity template = new IndustryTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setName(name);
        template.setDescription(description);
        template.setCategory(category);
        template.setSubcategory(subcategory);
        template.setKeywords(keywords);
        template.setReferenceUrl(referenceUrl);
        template.setComplexityScore(complexityScore);
        template.setUsageCount(usageCount);
        template.setRating(rating);
        template.setIsActive(true);

        // 设置简化的entities和features（避免复杂的Map结构在测试中出错）
        template.setEntities(new ArrayList<>()); // 空列表
        template.setFeatures(Arrays.asList("功能1", "功能2", "功能3"));
        template.setWorkflows(new ArrayList<>()); // 空列表

        return template;
    }
}
