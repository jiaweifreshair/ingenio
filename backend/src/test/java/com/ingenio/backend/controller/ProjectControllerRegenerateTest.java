package com.ingenio.backend.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.dto.CreateVersionRequest;
import com.ingenio.backend.dto.request.RegenerateRequest;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.service.AppSpecService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ProjectController regenerate API测试
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerRegenerateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private VersionService versionService;

    @MockBean
    private AppSpecService appSpecService;

    private UUID testProjectId;
    private UUID testUserId;
    private UUID testTenantId;
    private UUID testAppSpecId;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testTenantId = UUID.randomUUID();
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

        try (MockedStatic<StpUtil> stpUtilMock = mockStatic(StpUtil.class)) {
            stpUtilMock.when(StpUtil::getLoginIdAsString).thenReturn(testUserId.toString());
            stpUtilMock.when(StpUtil::getSession).thenReturn(mock(cn.dev33.satoken.session.SaSession.class));

            mockMvc.perform(post("/v1/projects/{id}/regenerate", testProjectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedStyle\":\"A\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.projectId").value(testProjectId.toString()))
                    .andExpect(jsonPath("$.data.newVersionId").exists())
                    .andExpect(jsonPath("$.data.newVersion").value(2));

            verify(versionService).createVersion(any(CreateVersionRequest.class));
            verify(appSpecService).updateById(any(AppSpecEntity.class));
        }
    }
}
