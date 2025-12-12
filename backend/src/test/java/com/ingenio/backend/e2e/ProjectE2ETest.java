package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.config.TestSaTokenConfig;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.ProjectMapper;
import com.ingenio.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ProjectController E2E测试
 *
 * 测试场景：
 * 1. 项目CRUD操作（创建、查询、更新、删除）
 * 2. 分页查询用户项目列表
 * 3. 查询公开项目（社区广场）
 * 4. 项目社交互动（点赞、收藏、派生）
 * 5. 项目状态管理（发布、归档）
 * 6. 项目统计数据查询
 *
 * 测试策略：
 * - 使用真实PostgreSQL数据库（TestContainers）
 * - 零Mock策略：使用真实Service和Mapper
 * - 每个测试前初始化测试数据
 * - 验证数据库状态确保正确性
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@DisplayName("项目管理API E2E测试")
public class ProjectE2ETest extends BaseE2ETest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AppSpecMapper appSpecMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private UUID testTenantId;
    private UUID testProjectId;
    private UUID testAppSpecId;

    /**
     * 每个测试前初始化测试数据
     */
    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        // 使用TestSaTokenConfig中的固定测试用户ID和租户ID
        testUserId = TestSaTokenConfig.TEST_USER_ID;
        testTenantId = TestSaTokenConfig.TEST_TENANT_ID;

        // 创建测试用户（满足外键约束）
        // 注意：使用固定ID，如果已存在则跳过创建
        UserEntity existingUser = userMapper.selectById(testUserId);
        if (existingUser == null) {
            UserEntity testUser = UserEntity.builder()
                .id(testUserId)
                .tenantId(testTenantId)
                .username("test-project-user")
                .email("test-project@ingenio.test")
                .passwordHash("test-password-hash")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            userMapper.insert(testUser);
        }

        // 创建测试AppSpec（满足外键约束）
        testAppSpecId = UUID.randomUUID();
        Map<String, Object> specContent = new HashMap<>();
        specContent.put("pages", Map.of("home", Map.of("title", "首页")));
        specContent.put("dataModels", Map.of("User", Map.of("fields", "id,name")));

        AppSpecEntity appSpec = AppSpecEntity.builder()
            .id(testAppSpecId)
            .tenantId(testTenantId)
            .createdByUserId(testUserId)
            .specContent(specContent)
            .version(1)
            .status(AppSpecEntity.Status.DRAFT.getValue())
            .qualityScore(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .metadata(new HashMap<>())
            .build();
        appSpecMapper.insert(appSpec);

        // 创建测试项目
        testProjectId = UUID.randomUUID();
        ProjectEntity project = ProjectEntity.builder()
            .id(testProjectId)
            .tenantId(testTenantId)
            .userId(testUserId)
            .name("测试项目")
            .description("这是一个测试项目")
            .coverImageUrl("https://example.com/cover.jpg")
            .appSpecId(testAppSpecId) // 关联测试AppSpec
            .status(ProjectEntity.Status.DRAFT.getValue())
            .visibility(ProjectEntity.Visibility.PRIVATE.getValue())
            .viewCount(0)
            .likeCount(0)
            .forkCount(0)
            .commentCount(0)
            .tags(Arrays.asList("教育", "工具"))
            .ageGroup(ProjectEntity.AgeGroup.MIDDLE_SCHOOL.getValue())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        projectMapper.insert(project);
    }

    @Test
    @DisplayName("测试1: 创建项目 - 成功场景")
    public void testCreateProject_Success() throws Exception {
        // 准备请求数据
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "新项目");
        requestBody.put("description", "新项目描述");
        requestBody.put("coverImageUrl", "https://example.com/new-cover.jpg");
        requestBody.put("appSpecId", testAppSpecId.toString()); // 使用已存在的AppSpec
        requestBody.put("visibility", "public");
        requestBody.put("tags", Arrays.asList("游戏", "编程"));
        requestBody.put("ageGroup", "high_school");

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // 执行POST请求
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.name").value("新项目"))
                .andExpect(jsonPath("$.data.visibility").value("public"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();

        // 提取创建的项目ID
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        String projectId = ((Map<String, Object>) responseMap.get("data")).get("id").toString();

        // 验证数据库中记录已创建
        ProjectEntity createdProject = projectMapper.selectById(UUID.fromString(projectId));
        assertNotNull(createdProject, "数据库中应存在创建的项目记录");
        assertEquals("新项目", createdProject.getName());
        assertEquals("draft", createdProject.getStatus());
    }

    @Test
    @DisplayName("测试2: 查询项目详情 - 成功场景")
    public void testGetProject_Success() throws Exception {
        // 执行GET请求
        mockMvc.perform(get("/api/v1/projects/{id}", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.id").value(testProjectId.toString()))
                .andExpect(jsonPath("$.data.name").value("测试项目"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.visibility").value("private"));

        // 验证浏览次数已增加
        ProjectEntity project = projectMapper.selectById(testProjectId);
        assertEquals(1, project.getViewCount(), "浏览次数应已增加到1");
    }

    @Test
    @DisplayName("测试3: 更新项目 - 成功场景")
    public void testUpdateProject_Success() throws Exception {
        // 准备更新请求
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "更新后的项目名称");
        requestBody.put("description", "更新后的描述");
        requestBody.put("appSpecId", testAppSpecId.toString()); // AppSpec必须提供
        requestBody.put("visibility", "public");

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // 执行PUT请求
        mockMvc.perform(put("/api/v1/projects/{id}", testProjectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.name").value("更新后的项目名称"))
                .andExpect(jsonPath("$.data.visibility").value("public"));

        // 验证数据库中记录已更新
        ProjectEntity updatedProject = projectMapper.selectById(testProjectId);
        assertEquals("更新后的项目名称", updatedProject.getName());
        assertEquals("public", updatedProject.getVisibility());
    }

    @Test
    @DisplayName("测试4: 删除项目 - 软删除成功")
    public void testDeleteProject_Success() throws Exception {
        // 执行DELETE请求
        mockMvc.perform(delete("/api/v1/projects/{id}", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("删除成功"));

        // 验证数据库中记录已被软删除
        ProjectEntity deletedProject = projectMapper.selectById(testProjectId);
        assertNull(deletedProject, "项目应已被软删除，查询应返回null");
    }

    @Test
    @DisplayName("测试5: 分页查询用户项目列表 - 成功场景")
    public void testListUserProjects_Success() throws Exception {
        // 创建多个测试项目
        for (int i = 1; i <= 5; i++) {
            UUID newProjectId = UUID.randomUUID();
            ProjectEntity project = ProjectEntity.builder()
                .id(newProjectId)
                .tenantId(testTenantId)
                .userId(testUserId)
                .name("项目" + i)
                .description("描述" + i)
                .appSpecId(testAppSpecId) // 使用相同的AppSpec
                .status(ProjectEntity.Status.DRAFT.getValue())
                .visibility(ProjectEntity.Visibility.PRIVATE.getValue())
                .viewCount(0)
                .likeCount(0)
                .forkCount(0)
                .commentCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            projectMapper.insert(project);
        }

        // 执行GET请求查询列表
        mockMvc.perform(get("/api/v1/projects")
                .param("current", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.current").value(1));
    }

    @Test
    @DisplayName("测试6: 查询公开项目（社区广场）- 成功场景")
    public void testListPublicProjects_Success() throws Exception {
        // 创建公开项目
        UUID publicProjectId = UUID.randomUUID();
        ProjectEntity publicProject = ProjectEntity.builder()
            .id(publicProjectId)
            .tenantId(testTenantId)
            .userId(testUserId)
            .name("公开项目")
            .description("公开项目描述")
            .appSpecId(testAppSpecId)
            .status(ProjectEntity.Status.PUBLISHED.getValue())
            .visibility(ProjectEntity.Visibility.PUBLIC.getValue())
            .viewCount(10)
            .likeCount(5)
            .forkCount(2)
            .commentCount(3)
            .publishedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        projectMapper.insert(publicProject);

        // 执行GET请求查询公开项目（无需登录）
        mockMvc.perform(get("/api/v1/projects/public")
                .param("current", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("测试7: 派生项目（Fork）- 成功场景")
    public void testForkProject_Success() throws Exception {
        // 执行POST请求派生项目
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/fork", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.name").value(containsString("测试项目")))
                .andExpect(jsonPath("$.data.userId").value(testUserId.toString()))
                .andReturn();

        // 提取派生后的项目ID
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        String forkedProjectId = ((Map<String, Object>) responseMap.get("data")).get("id").toString();

        // 验证数据库中派生项目已创建
        ProjectEntity forkedProject = projectMapper.selectById(UUID.fromString(forkedProjectId));
        assertNotNull(forkedProject, "派生项目应已创建");
        assertEquals(testUserId, forkedProject.getUserId(), "派生项目所有者应为当前用户");

        // 验证原项目的fork计数已增加
        ProjectEntity sourceProject = projectMapper.selectById(testProjectId);
        assertEquals(1, sourceProject.getForkCount(), "原项目的fork计数应为1");
    }

    @Test
    @DisplayName("测试8: 点赞项目 - 成功场景")
    public void testLikeProject_Success() throws Exception {
        // 执行POST请求点赞
        mockMvc.perform(post("/api/v1/projects/{id}/like", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("点赞成功"));

        // 验证数据库中点赞数已增加
        ProjectEntity project = projectMapper.selectById(testProjectId);
        assertEquals(1, project.getLikeCount(), "点赞数应为1");
    }

    @Test
    @DisplayName("测试9: 取消点赞项目 - 成功场景")
    public void testUnlikeProject_Success() throws Exception {
        // 先点赞
        mockMvc.perform(post("/api/v1/projects/{id}/like", testProjectId));

        // 执行DELETE请求取消点赞
        mockMvc.perform(delete("/api/v1/projects/{id}/like", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("取消点赞成功"));

        // 验证数据库中点赞数已减少
        ProjectEntity project = projectMapper.selectById(testProjectId);
        assertEquals(0, project.getLikeCount(), "点赞数应为0");
    }

    @Test
    @DisplayName("测试10: 收藏项目 - 成功场景")
    public void testFavoriteProject_Success() throws Exception {
        // 执行POST请求收藏
        mockMvc.perform(post("/api/v1/projects/{id}/favorite", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("收藏成功"));
    }

    @Test
    @DisplayName("测试11: 取消收藏项目 - 成功场景")
    public void testUnfavoriteProject_Success() throws Exception {
        // 先收藏
        mockMvc.perform(post("/api/v1/projects/{id}/favorite", testProjectId));

        // 执行DELETE请求取消收藏
        mockMvc.perform(delete("/api/v1/projects/{id}/favorite", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("取消收藏成功"));
    }

    @Test
    @DisplayName("测试12: 发布项目 - 成功场景")
    public void testPublishProject_Success() throws Exception {
        // 执行POST请求发布项目
        mockMvc.perform(post("/api/v1/projects/{id}/publish", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("发布成功"));

        // 验证数据库中状态已更新
        ProjectEntity project = projectMapper.selectById(testProjectId);
        assertEquals("published", project.getStatus(), "项目状态应为published");
        assertNotNull(project.getPublishedAt(), "发布时间应已设置");
    }

    @Test
    @DisplayName("测试13: 归档项目 - 成功场景")
    public void testArchiveProject_Success() throws Exception {
        // 执行POST请求归档项目
        mockMvc.perform(post("/api/v1/projects/{id}/archive", testProjectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("归档成功"));

        // 验证数据库中状态已更新
        ProjectEntity project = projectMapper.selectById(testProjectId);
        assertEquals("archived", project.getStatus(), "项目状态应为archived");
    }

    @Test
    @DisplayName("测试14: 查询项目统计数据 - 成功场景")
    public void testGetProjectStats_Success() throws Exception {
        // 创建多个不同状态的项目
        createProjectWithStatus(ProjectEntity.Status.PUBLISHED.getValue());
        createProjectWithStatus(ProjectEntity.Status.ARCHIVED.getValue());

        // 执行GET请求查询统计数据
        mockMvc.perform(get("/api/v1/projects/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.totalProjects", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.publishedProjects", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.archivedProjects", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.draftProjects", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("测试15: 按状态筛选项目列表 - 成功场景")
    public void testListProjectsByStatus_Success() throws Exception {
        // 创建已发布项目
        createProjectWithStatus(ProjectEntity.Status.PUBLISHED.getValue());

        // 执行GET请求，筛选已发布项目
        mockMvc.perform(get("/api/v1/projects")
                .param("current", "1")
                .param("size", "10")
                .param("status", "published")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.records[*].status", everyItem(equalTo("published"))));
    }

    @Test
    @DisplayName("测试16: 关键词搜索项目 - 成功场景")
    public void testSearchProjects_Success() throws Exception {
        // 创建包含特定关键词的项目
        UUID searchProjectId = UUID.randomUUID();
        ProjectEntity searchProject = ProjectEntity.builder()
            .id(searchProjectId)
            .tenantId(testTenantId)
            .userId(testUserId)
            .name("图书馆管理系统")
            .description("用于学校图书馆的图书借阅管理")
            .appSpecId(testAppSpecId)
            .status(ProjectEntity.Status.DRAFT.getValue())
            .visibility(ProjectEntity.Visibility.PRIVATE.getValue())
            .viewCount(0)
            .likeCount(0)
            .forkCount(0)
            .commentCount(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        projectMapper.insert(searchProject);

        // 执行GET请求，使用关键词搜索
        mockMvc.perform(get("/api/v1/projects")
                .param("current", "1")
                .param("size", "10")
                .param("keyword", "图书")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.records[*].name", hasItem(containsString("图书"))));
    }

    /**
     * 辅助方法：创建指定状态的项目
     *
     * @param status 项目状态
     */
    private void createProjectWithStatus(String status) {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = ProjectEntity.builder()
            .id(projectId)
            .tenantId(testTenantId)
            .userId(testUserId)
            .name("项目-" + status)
            .description("状态为" + status + "的项目")
            .appSpecId(testAppSpecId)
            .status(status)
            .visibility(ProjectEntity.Visibility.PRIVATE.getValue())
            .viewCount(0)
            .likeCount(0)
            .forkCount(0)
            .commentCount(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        if ("published".equals(status)) {
            project.setPublishedAt(Instant.now());
        }

        projectMapper.insert(project);
    }
}
