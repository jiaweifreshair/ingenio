package com.ingenio.backend.controller;

import com.ingenio.backend.config.TestSaTokenConfig;
import com.ingenio.backend.dto.CreateVersionRequest;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.service.AppSpecService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.VersionService;
import com.ingenio.backend.service.g3.G3OrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectController regenerate API测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSaTokenConfig.class)
class ProjectControllerRegenerateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private VersionService versionService;

    @MockBean
    private AppSpecService appSpecService;

    @MockBean
    private G3OrchestratorService g3OrchestratorService;

    private UUID testProjectId;
    private UUID testUserId;
    private UUID testTenantId;
    private UUID testAppSpecId;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();
        testUserId = TestSaTokenConfig.TEST_USER_ID;
        testTenantId = TestSaTokenConfig.TEST_TENANT_ID;
        testAppSpecId = UUID.randomUUID();
    }

    @Test
    void testRegenerateSuccess() throws Exception {
        ProjectEntity project = ProjectEntity.builder()
                .id(testProjectId)
                .appSpecId(testAppSpecId)
                .build();

        AppSpecEntity newVersion = new AppSpecEntity();
        newVersion.setId(UUID.randomUUID());
        newVersion.setVersion(2);

        when(projectService.getByIdAndTenantId(testProjectId, testTenantId)).thenReturn(project);
        when(versionService.createVersion(any(CreateVersionRequest.class))).thenReturn(newVersion);
        when(appSpecService.updateById(any(AppSpecEntity.class))).thenReturn(true);
        when(g3OrchestratorService.submitJob(any(), any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/v1/projects/{id}/regenerate", testProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedStyle\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.projectId").value(testProjectId.toString()))
                .andExpect(jsonPath("$.data.newVersionId").exists())
                .andExpect(jsonPath("$.data.newVersion").value(2));

        verify(versionService).createVersion(any(CreateVersionRequest.class));
        verify(appSpecService).updateById(any(AppSpecEntity.class));
        verify(g3OrchestratorService).submitJob(any(), any(), any(), any(), any(), any());
    }
}
