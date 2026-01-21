package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3PlanningFileEntity;
import com.ingenio.backend.mapper.g3.G3PlanningFileMapper;
import com.ingenio.backend.service.g3.template.TaskPlanTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * G3PlanningFileService 测试
 */
@ExtendWith(MockitoExtension.class)
class G3PlanningFileServiceTest {

    @Mock
    private G3PlanningFileMapper planningFileMapper;

    @Mock
    private TaskPlanTemplate taskPlanTemplate;

    @InjectMocks
    private G3PlanningFileService planningFileService;

    private UUID testJobId;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
    }

    @Test
    void testCreatePlanningFile() {
        // Given
        String fileType = "task_plan";
        String content = "# Task Plan\n\nTest content";
        String updatedBy = "system";

        when(planningFileMapper.insert(any(G3PlanningFileEntity.class))).thenReturn(1);

        // When
        G3PlanningFileEntity result = planningFileService.createPlanningFile(testJobId, fileType, content, updatedBy);

        // Then
        assertNotNull(result);
        assertEquals(testJobId, result.getJobId());
        assertEquals(fileType, result.getFileType());
        assertEquals(content, result.getContent());
        verify(planningFileMapper, times(1)).insert(any(G3PlanningFileEntity.class));
    }

    @Test
    void testGetByJobAndType() {
        // Given
        String fileType = "task_plan";
        G3PlanningFileEntity entity = new G3PlanningFileEntity();
        entity.setJobId(testJobId);
        entity.setFileType(fileType);
        entity.setContent("Test content");

        when(planningFileMapper.selectOne(any())).thenReturn(entity);

        // When
        Optional<G3PlanningFileEntity> result = planningFileService.getByJobAndType(testJobId, fileType);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testJobId, result.get().getJobId());
        assertEquals(fileType, result.get().getFileType());
        verify(planningFileMapper, times(1)).selectOne(any());
    }

    @Test
    void testGetContent() {
        // Given
        String fileType = "task_plan";
        String content = "Test content";
        G3PlanningFileEntity entity = new G3PlanningFileEntity();
        entity.setContent(content);

        when(planningFileMapper.selectOne(any())).thenReturn(entity);

        // When
        String result = planningFileService.getContent(testJobId, fileType);

        // Then
        assertEquals(content, result);
        verify(planningFileMapper, times(1)).selectOne(any());
    }
}
