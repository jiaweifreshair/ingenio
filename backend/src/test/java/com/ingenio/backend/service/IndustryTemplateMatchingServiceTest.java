package com.ingenio.backend.service;

import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IndustryTemplateMatchingService单元测试
 *
 * Phase X.4: 行业模板库功能 - 模板匹配算法测试
 *
 * 测试覆盖:
 * 1. Jaccard相似度计算
 * 2. URL相似度计算
 * 3. 复杂度惩罚计算
 * 4. 分类匹配计算
 * 5. 整体模板匹配算法
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.4 Task 6)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndustryTemplateMatchingServiceTest {

    @Mock
    private IndustryTemplateMapper templateMapper;

    private IndustryTemplateMatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new IndustryTemplateMatchingService();
        // 使用反射注入mock的mapper
        try {
            var field = IndustryTemplateMatchingService.class.getDeclaredField("templateMapper");
            field.setAccessible(true);
            field.set(matchingService, templateMapper);
        } catch (Exception e) {
            fail("Failed to inject templateMapper: " + e.getMessage());
        }
    }

    /**
     * 测试Jaccard相似度计算 - 完全匹配
     */
    @Test
    void testCalculateKeywordSimilarity_ExactMatch() throws Exception {
        // 准备测试数据
        List<String> userKeywords = Arrays.asList("民宿", "预订", "airbnb");
        List<String> templateKeywords = Arrays.asList("民宿", "预订", "airbnb");

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateKeywordSimilarity",
            List.class, List.class,
            userKeywords, templateKeywords
        );

        // 验证结果: 完全匹配应该是1.0
        assertEquals(1.0, similarity, 0.001);
    }

    /**
     * 测试Jaccard相似度计算 - 部分匹配
     */
    @Test
    void testCalculateKeywordSimilarity_PartialMatch() throws Exception {
        // 准备测试数据: 交集2个,并集4个
        List<String> userKeywords = Arrays.asList("民宿", "预订", "住宿");
        List<String> templateKeywords = Arrays.asList("民宿", "预订", "airbnb", "短租");

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateKeywordSimilarity",
            List.class, List.class,
            userKeywords, templateKeywords
        );

        // 验证结果: 2/5 = 0.4
        assertEquals(0.4, similarity, 0.001);
    }

    /**
     * 测试Jaccard相似度计算 - 无匹配
     */
    @Test
    void testCalculateKeywordSimilarity_NoMatch() throws Exception {
        // 准备测试数据: 完全不同
        List<String> userKeywords = Arrays.asList("电商", "购物", "商城");
        List<String> templateKeywords = Arrays.asList("民宿", "预订", "airbnb");

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateKeywordSimilarity",
            List.class, List.class,
            userKeywords, templateKeywords
        );

        // 验证结果: 0/6 = 0.0
        assertEquals(0.0, similarity, 0.001);
    }

    /**
     * 测试Jaccard相似度计算 - 空列表
     */
    @Test
    void testCalculateKeywordSimilarity_EmptyList() throws Exception {
        // 准备测试数据
        List<String> userKeywords = Arrays.asList();
        List<String> templateKeywords = Arrays.asList("民宿", "预订");

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateKeywordSimilarity",
            List.class, List.class,
            userKeywords, templateKeywords
        );

        // 验证结果: 空列表应该返回0.0
        assertEquals(0.0, similarity, 0.001);
    }

    /**
     * 测试Jaccard相似度计算 - 忽略大小写
     */
    @Test
    void testCalculateKeywordSimilarity_CaseInsensitive() throws Exception {
        // 准备测试数据: 不同大小写
        List<String> userKeywords = Arrays.asList("AIRBNB", "Booking", "hotel");
        List<String> templateKeywords = Arrays.asList("airbnb", "booking", "HOTEL");

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateKeywordSimilarity",
            List.class, List.class,
            userKeywords, templateKeywords
        );

        // 验证结果: 应该完全匹配
        assertEquals(1.0, similarity, 0.001);
    }

    /**
     * 测试URL相似度计算 - 完全匹配
     */
    @Test
    void testCalculateUrlSimilarity_ExactMatch() throws Exception {
        // 准备测试数据
        String userUrl = "https://www.airbnb.com";
        String templateUrl = "https://www.airbnb.com";

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateUrlSimilarity",
            String.class, String.class,
            userUrl, templateUrl
        );

        // 验证结果: 完全匹配应该是1.0
        assertEquals(1.0, similarity, 0.001);
    }

    /**
     * 测试URL相似度计算 - 主域名匹配
     */
    @Test
    void testCalculateUrlSimilarity_MainDomainMatch() throws Exception {
        // 准备测试数据: 子域名不同但主域名相同
        String userUrl = "https://www.airbnb.com";
        String templateUrl = "https://cn.airbnb.com";

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateUrlSimilarity",
            String.class, String.class,
            userUrl, templateUrl
        );

        // 验证结果: 主域名匹配应该是0.5
        assertEquals(0.5, similarity, 0.001);
    }

    /**
     * 测试URL相似度计算 - 不匹配
     */
    @Test
    void testCalculateUrlSimilarity_NoMatch() throws Exception {
        // 准备测试数据: 完全不同的域名
        String userUrl = "https://www.airbnb.com";
        String templateUrl = "https://www.booking.com";

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateUrlSimilarity",
            String.class, String.class,
            userUrl, templateUrl
        );

        // 验证结果: 不匹配应该是0.0
        assertEquals(0.0, similarity, 0.001);
    }

    /**
     * 测试URL相似度计算 - 空URL
     */
    @Test
    void testCalculateUrlSimilarity_EmptyUrl() throws Exception {
        // 准备测试数据
        String userUrl = "";
        String templateUrl = "https://www.airbnb.com";

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateUrlSimilarity",
            String.class, String.class,
            userUrl, templateUrl
        );

        // 验证结果: 空URL应该返回0.0
        assertEquals(0.0, similarity, 0.001);
    }

    /**
     * 测试URL相似度计算 - 忽略协议和路径
     */
    @Test
    void testCalculateUrlSimilarity_IgnoreProtocolAndPath() throws Exception {
        // 准备测试数据: 不同协议和路径
        String userUrl = "http://www.airbnb.com/search/homes";
        String templateUrl = "https://www.airbnb.com/about";

        // 调用private方法
        double similarity = invokePrivateMethod(
            "calculateUrlSimilarity",
            String.class, String.class,
            userUrl, templateUrl
        );

        // 验证结果: 域名相同应该是1.0
        assertEquals(1.0, similarity, 0.001);
    }

    /**
     * 测试复杂度惩罚计算 - 最低复杂度
     */
    @Test
    void testCalculateComplexityPenalty_MinComplexity() throws Exception {
        // 准备测试数据: 复杂度1
        Integer complexityScore = 1;

        // 调用private方法
        double penalty = invokePrivateMethod(
            "calculateComplexityPenalty",
            Integer.class,
            complexityScore
        );

        // 验证结果: 1/10 = 0.1
        assertEquals(0.1, penalty, 0.001);
    }

    /**
     * 测试复杂度惩罚计算 - 中等复杂度
     */
    @Test
    void testCalculateComplexityPenalty_MediumComplexity() throws Exception {
        // 准备测试数据: 复杂度5
        Integer complexityScore = 5;

        // 调用private方法
        double penalty = invokePrivateMethod(
            "calculateComplexityPenalty",
            Integer.class,
            complexityScore
        );

        // 验证结果: 5/10 = 0.5
        assertEquals(0.5, penalty, 0.001);
    }

    /**
     * 测试复杂度惩罚计算 - 最高复杂度
     */
    @Test
    void testCalculateComplexityPenalty_MaxComplexity() throws Exception {
        // 准备测试数据: 复杂度10
        Integer complexityScore = 10;

        // 调用private方法
        double penalty = invokePrivateMethod(
            "calculateComplexityPenalty",
            Integer.class,
            complexityScore
        );

        // 验证结果: 10/10 = 1.0
        assertEquals(1.0, penalty, 0.001);
    }

    /**
     * 测试复杂度惩罚计算 - null值
     */
    @Test
    void testCalculateComplexityPenalty_Null() throws Exception {
        // 准备测试数据: null
        Integer complexityScore = null;

        // 调用private方法
        double penalty = invokePrivateMethod(
            "calculateComplexityPenalty",
            Integer.class,
            complexityScore
        );

        // 验证结果: null应该返回0.0
        assertEquals(0.0, penalty, 0.001);
    }

    /**
     * 测试分类匹配计算 - 一级分类匹配
     */
    @Test
    void testCalculateCategoryScore_PrimaryCategoryMatch() throws Exception {
        // 准备测试数据: 包含"生活服务"相关关键词
        List<String> userKeywords = Arrays.asList("民宿", "预订", "住宿");
        String templateCategory = "生活服务";
        String templateSubcategory = "住宿预订";

        // 调用private方法
        double score = invokePrivateMethod(
            "calculateCategoryScore",
            List.class, String.class, String.class,
            userKeywords, templateCategory, templateSubcategory
        );

        // 验证结果: 一级分类匹配应该是1.0
        assertEquals(1.0, score, 0.001);
    }

    /**
     * 测试分类匹配计算 - 不匹配
     */
    @Test
    void testCalculateCategoryScore_NoMatch() throws Exception {
        // 准备测试数据: 关键词与分类不相关
        List<String> userKeywords = Arrays.asList("abc", "xyz", "123");
        String templateCategory = "生活服务";
        String templateSubcategory = "住宿预订";

        // 调用private方法
        double score = invokePrivateMethod(
            "calculateCategoryScore",
            List.class, String.class, String.class,
            userKeywords, templateCategory, templateSubcategory
        );

        // 验证结果: 不匹配应该是0.0
        assertEquals(0.0, score, 0.001);
    }

    /**
     * 测试整体模板匹配算法
     */
    @Test
    void testMatchTemplates_Success() {
        // 准备测试数据
        List<String> userKeywords = Arrays.asList("民宿", "预订", "airbnb");
        String referenceUrl = "https://www.airbnb.com";
        int topN = 3;

        // 创建测试模板
        List<IndustryTemplateEntity> mockTemplates = createMockTemplates();

        // 模拟Mapper行为
        when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(mockTemplates);

        // 执行匹配
        List<IndustryTemplateMatchingService.TemplateMatchResult> results =
            matchingService.matchTemplates(userKeywords, referenceUrl, topN);

        // 验证结果
        assertNotNull(results);
        assertTrue(results.size() <= topN, "返回结果数量应该不超过topN");
        assertTrue(results.size() > 0, "应该至少返回一个匹配结果");

        // 验证结果按分数降序排列
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(
                results.get(i).getTotalScore() >= results.get(i + 1).getTotalScore(),
                "结果应该按总分降序排列"
            );
        }

        // 验证Mapper被调用
        verify(templateMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试整体模板匹配算法 - 空关键词
     */
    @Test
    void testMatchTemplates_EmptyKeywords() {
        // 准备测试数据
        List<String> userKeywords = Arrays.asList();
        String referenceUrl = "https://www.airbnb.com";
        int topN = 3;

        // 创建测试模板
        List<IndustryTemplateEntity> mockTemplates = createMockTemplates();

        // 模拟Mapper行为
        when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(mockTemplates);

        // 执行匹配
        List<IndustryTemplateMatchingService.TemplateMatchResult> results =
            matchingService.matchTemplates(userKeywords, referenceUrl, topN);

        // 验证结果: 空关键词应该返回空结果或低分结果
        assertNotNull(results);

        // 验证Mapper被调用
        verify(templateMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试整体模板匹配算法 - 无启用模板
     */
    @Test
    void testMatchTemplates_NoActiveTemplates() {
        // 准备测试数据
        List<String> userKeywords = Arrays.asList("民宿", "预订");
        String referenceUrl = "https://www.airbnb.com";
        int topN = 3;

        // 模拟Mapper行为: 返回空列表
        when(templateMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        // 执行匹配
        List<IndustryTemplateMatchingService.TemplateMatchResult> results =
            matchingService.matchTemplates(userKeywords, referenceUrl, topN);

        // 验证结果: 应该返回空列表
        assertNotNull(results);
        assertEquals(0, results.size());

        // 验证Mapper被调用
        verify(templateMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 测试URL提取域名
     */
    @Test
    void testExtractDomain() throws Exception {
        // 测试完整URL
        String domain1 = invokePrivateMethod(
            "extractDomain",
            String.class,
            "https://www.airbnb.com/search/homes?query=beijing"
        );
        assertEquals("www.airbnb.com", domain1);

        // 测试无协议URL
        String domain2 = invokePrivateMethod(
            "extractDomain",
            String.class,
            "www.airbnb.com/search/homes"
        );
        assertEquals("www.airbnb.com", domain2);

        // 测试带端口URL
        String domain3 = invokePrivateMethod(
            "extractDomain",
            String.class,
            "https://www.airbnb.com:8080/search"
        );
        assertEquals("www.airbnb.com", domain3);
    }

    /**
     * 测试提取主域名
     */
    @Test
    void testExtractMainDomain() throws Exception {
        // 测试标准域名
        String mainDomain1 = invokePrivateMethod(
            "extractMainDomain",
            String.class,
            "www.airbnb.com"
        );
        assertEquals("airbnb.com", mainDomain1);

        // 测试子域名
        String mainDomain2 = invokePrivateMethod(
            "extractMainDomain",
            String.class,
            "cn.www.airbnb.com"
        );
        assertEquals("airbnb.com", mainDomain2);

        // 测试单级域名
        String mainDomain3 = invokePrivateMethod(
            "extractMainDomain",
            String.class,
            "localhost"
        );
        assertEquals("localhost", mainDomain3);
    }

    // ==================== Helper Methods ====================

    /**
     * 调用private方法的辅助方法
     */
    @SuppressWarnings("unchecked")
    private <T> T invokePrivateMethod(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = IndustryTemplateMatchingService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(matchingService, args);
    }

    /**
     * 重载版本: 单个参数类型
     */
    private <T> T invokePrivateMethod(String methodName, Class<?> parameterType, Object arg) throws Exception {
        return invokePrivateMethod(methodName, new Class<?>[]{parameterType}, arg);
    }

    /**
     * 重载版本: 两个参数类型
     */
    private <T> T invokePrivateMethod(String methodName, Class<?> type1, Class<?> type2, Object arg1, Object arg2) throws Exception {
        return invokePrivateMethod(methodName, new Class<?>[]{type1, type2}, arg1, arg2);
    }

    /**
     * 重载版本: 三个参数类型
     */
    private <T> T invokePrivateMethod(String methodName, Class<?> type1, Class<?> type2, Class<?> type3,
                                      Object arg1, Object arg2, Object arg3) throws Exception {
        return invokePrivateMethod(methodName, new Class<?>[]{type1, type2, type3}, arg1, arg2, arg3);
    }

    /**
     * 创建测试用的模板数据
     */
    private List<IndustryTemplateEntity> createMockTemplates() {
        List<IndustryTemplateEntity> templates = new ArrayList<>();

        // 模板1: 高度匹配的民宿模板
        templates.add(IndustryTemplateEntity.builder()
            .id(UUID.randomUUID())
            .name("民宿预订平台模板")
            .category("生活服务")
            .subcategory("住宿预订")
            .keywords(Arrays.asList("民宿", "预订", "住宿", "airbnb", "短租"))
            .referenceUrl("https://www.airbnb.com")
            .complexityScore(6)
            .usageCount(100)
            .rating(new BigDecimal("4.5"))
            .isActive(true)
            .build());

        // 模板2: 中等匹配的酒店模板
        templates.add(IndustryTemplateEntity.builder()
            .id(UUID.randomUUID())
            .name("酒店预订模板")
            .category("生活服务")
            .subcategory("住宿预订")
            .keywords(Arrays.asList("酒店", "预订", "住宿", "booking"))
            .referenceUrl("https://www.booking.com")
            .complexityScore(7)
            .usageCount(80)
            .rating(new BigDecimal("4.0"))
            .isActive(true)
            .build());

        // 模板3: 低匹配的电商模板
        templates.add(IndustryTemplateEntity.builder()
            .id(UUID.randomUUID())
            .name("电商平台模板")
            .category("电商")
            .subcategory("综合电商")
            .keywords(Arrays.asList("电商", "商城", "购物", "淘宝"))
            .referenceUrl("https://www.taobao.com")
            .complexityScore(8)
            .usageCount(120)
            .rating(new BigDecimal("4.8"))
            .isActive(true)
            .build());

        return templates;
    }
}
