package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import com.ingenio.backend.dto.response.GenerateFullResponse;
import com.ingenio.backend.service.GenerateService;
import com.ingenio.backend.service.IGenerationTaskService;
import com.ingenio.backend.service.NLRequirementAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenerateController Unit Tests")
class GenerateControllerTest {

    @Mock
    private GenerateService generateService;

    @Mock
    private IGenerationTaskService generationTaskService;

    @Mock
    private NLRequirementAnalyzer nlRequirementAnalyzer;

    @Mock
    private ObjectMapper objectMapper;

    private GenerateController generateController;

    @BeforeEach
    void setUp() {
        generateController = new GenerateController(generateService, generationTaskService, nlRequirementAnalyzer, objectMapper);
    }

    @Test
    @DisplayName("generateFull - Should return success response")
    void generateFull_ShouldReturnSuccessResponse() {
        // Given
        GenerateFullRequest request = new GenerateFullRequest();
        request.setUserRequirement("Create a Todo app");

        GenerateFullResponse response = GenerateFullResponse.builder()
                .appSpecId(UUID.randomUUID())
                .status("completed")
                .build();

        when(generateService.generateFull(any(GenerateFullRequest.class))).thenReturn(response);

        // When
        Result<GenerateFullResponse> result = generateController.generateFull(request);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
        assertEquals("completed", result.getData().getStatus());
    }

    @Test
    @DisplayName("createAsyncTask - Should return task ID")
    void createAsyncTask_ShouldReturnTaskId() {
        // Given
        GenerateFullRequest request = new GenerateFullRequest();
        request.setUserRequirement("Create a complex app");
        UUID taskId = UUID.randomUUID();

        when(generationTaskService.createAsyncTask(any(GenerateFullRequest.class))).thenReturn(taskId);

        // When
        Result<String> result = generateController.createAsyncTask(request);

        // Then
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(taskId.toString(), result.getData());
    }
}
