package com.ingenio.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.TemplateMatchRequest;
import com.ingenio.backend.dto.response.TemplateMatchResponse;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.mapper.IndustryTemplateMapper;
import com.ingenio.backend.service.IndustryTemplateMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndustryTemplateController Unit Tests")
class IndustryTemplateControllerTest {

    @Mock
    private IndustryTemplateMatchingService matchingService;

    @Mock
    private IndustryTemplateMapper templateMapper;

    private IndustryTemplateController industryTemplateController;

    @BeforeEach
    void setUp() {
        industryTemplateController = new IndustryTemplateController(matchingService, templateMapper);
    }

    @Test
    @DisplayName("matchTemplates - Should return matched templates")
    void matchTemplates_ShouldReturnMatchedTemplates() {
        // Given
        TemplateMatchRequest request = new TemplateMatchRequest();
        request.setKeywords(Arrays.asList("ecommerce", "shop"));
        request.setTopN(5);

        IndustryTemplateEntity template = IndustryTemplateEntity.builder()
                .id(UUID.randomUUID())
                .name("E-commerce Template")
                .build();

        IndustryTemplateMatchingService.TemplateMatchResult matchResult =
                new IndustryTemplateMatchingService.TemplateMatchResult(
                        template, 0.8, 0.5, 0.0, 0.1);

        when(matchingService.matchTemplates(any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(matchResult));

        // When
        Result<List<TemplateMatchResponse>> result = industryTemplateController.matchTemplates(request);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());
        assertEquals("E-commerce Template", result.getData().get(0).getTemplate().getName());
    }

    @Test
    @DisplayName("getTemplateById - Should return template when found")
    void getTemplateById_ShouldReturnTemplate_WhenFound() {
        // Given
        UUID templateId = UUID.randomUUID();
        IndustryTemplateEntity template = IndustryTemplateEntity.builder()
                .id(templateId)
                .name("Test Template")
                .isActive(true)
                .build();

        when(templateMapper.selectById(templateId)).thenReturn(template);

        // When
        Result<IndustryTemplateEntity> result = industryTemplateController.getTemplateById(templateId);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals("Test Template", result.getData().getName());
    }

    @Test
    @DisplayName("getTemplateById - Should return error when not found")
    void getTemplateById_ShouldReturnError_WhenNotFound() {
        // Given
        UUID templateId = UUID.randomUUID();
        when(templateMapper.selectById(templateId)).thenReturn(null);

        // When
        Result<IndustryTemplateEntity> result = industryTemplateController.getTemplateById(templateId);

        // Then
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertEquals(404, result.getCode());
        assertEquals("模板不存在", result.getMessage());
    }

    @Test
    @DisplayName("listTemplates - Should return list of templates")
    void listTemplates_ShouldReturnListOfTemplates() {
        // Given
        IndustryTemplateEntity template1 = IndustryTemplateEntity.builder().name("Template 1").build();
        IndustryTemplateEntity template2 = IndustryTemplateEntity.builder().name("Template 2").build();
        List<IndustryTemplateEntity> templates = Arrays.asList(template1, template2);

        when(templateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(templates);

        // When
        Result<List<IndustryTemplateEntity>> result = industryTemplateController.listTemplates(null, null, "usageCount", 10);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(2, result.getData().size());
    }
}
