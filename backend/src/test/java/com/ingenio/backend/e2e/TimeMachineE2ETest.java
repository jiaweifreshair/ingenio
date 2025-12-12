package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.VersionType;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import com.ingenio.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * TimeMachineController E2E测试
 *
 * 测试场景：
 * 1. 创建版本快照
 * 2. 获取版本时间线
 * 3. 版本差异对比
 * 4. 版本回滚
 * 5. 版本详情查询
 * 6. 版本删除
 *
 * 测试策略：使用真实PostgreSQL数据库（TestContainers）
 */
@DisplayName("时光机API E2E测试")
public class TimeMachineE2ETest extends BaseE2ETest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private GenerationVersionMapper versionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private UUID testTenantId;
    private UUID testTaskId;
    private UUID testVersionId1;
    private UUID testVersionId2;

    /**
     * 每个测试前初始化测试数据
     */
    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        // 注意：由于使用@Transactional，测试数据会自动回滚，无需手动清理

        // 创建测试用户（满足外键约束）
        testTenantId = UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        UserEntity testUser = UserEntity.builder()
            .tenantId(testTenantId)
            .username("test-user-" + timestamp)
            .email("test-" + timestamp + "@ingenio.test") // 动态email避免唯一约束冲突
            .passwordHash("test-password-hash")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        userMapper.insert(testUser);
        testUserId = testUser.getId();

        // 创建测试任务
        testTaskId = UUID.randomUUID();
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setId(testTaskId);
        task.setTenantId(testTenantId);
        task.setUserId(testUserId);
        task.setTaskName("图书管理系统-" + timestamp); // 任务名称
        task.setUserRequirement("创建一个图书管理系统");
        task.setStatus("executing"); // 使用有效的status值
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskMapper.insert(task);

        // 创建测试版本1（PLAN）
        testVersionId1 = UUID.randomUUID();
        GenerationVersionEntity version1 = new GenerationVersionEntity();
        version1.setId(testVersionId1);
        version1.setTenantId(testTenantId);
        version1.setUserId(testUserId);
        version1.setTaskId(testTaskId);
        version1.setVersionNumber(1);
        version1.setVersionType(VersionType.PLAN.name().toLowerCase());
        version1.setDescription("需求分析完成");
        Map<String, Object> snapshot1 = new HashMap<>();
        snapshot1.put("entities", Map.of("book", Map.of("name", "Book", "fields", "title,author,isbn")));
        snapshot1.put("techStack", Map.of("backend", "Spring Boot", "frontend", "React"));
        version1.setSnapshot(snapshot1);
        version1.setCreatedAt(Instant.now());
        versionMapper.insert(version1);

        // 创建测试版本2（SCHEMA）
        testVersionId2 = UUID.randomUUID();
        GenerationVersionEntity version2 = new GenerationVersionEntity();
        version2.setId(testVersionId2);
        version2.setTenantId(testTenantId);
        version2.setUserId(testUserId);
        version2.setTaskId(testTaskId);
        version2.setVersionNumber(2);
        version2.setVersionType(VersionType.SCHEMA.name().toLowerCase());
        version2.setDescription("数据库设计完成");
        Map<String, Object> snapshot2 = new HashMap<>();
        snapshot2.put("ddl", "CREATE TABLE book (id BIGSERIAL PRIMARY KEY, title VARCHAR(200))");
        snapshot2.put("entities", Map.of("book", Map.of("name", "Book", "fields", "id,title,author,isbn")));
        version2.setSnapshot(snapshot2);
        version2.setCreatedAt(Instant.now().plus(java.time.Duration.ofMinutes(5)));
        versionMapper.insert(version2);
    }

    @Test
    @DisplayName("测试1: 获取版本时间线")
    public void testGetTimeline() throws Exception {
        mockMvc.perform(get("/v1/timemachine/timeline/{taskId}", testTaskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].versionNumber").value(2))
                .andExpect(jsonPath("$.data[0].versionType").value(VersionType.SCHEMA.name().toLowerCase()))
                .andExpect(jsonPath("$.data[1].versionNumber").value(1))
                .andExpect(jsonPath("$.data[1].versionType").value(VersionType.PLAN.name().toLowerCase()));
    }

    @Test
    @DisplayName("测试2: 获取版本详情")
    public void testGetVersionDetail() throws Exception {
        mockMvc.perform(get("/v1/timemachine/version/{versionId}", testVersionId1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.versionType").value(VersionType.PLAN.name().toLowerCase()))
                .andExpect(jsonPath("$.data.description").value("需求分析完成"))
                .andExpect(jsonPath("$.data.snapshot.techStack.backend").value("Spring Boot"));
    }

    @Test
    @DisplayName("测试3: 对比版本差异")
    public void testCompareVersions() throws Exception {
        mockMvc.perform(get("/v1/timemachine/diff")
                        .param("version1", testVersionId1.toString())
                        .param("version2", testVersionId2.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version1.versionNumber").value(1))
                .andExpect(jsonPath("$.data.version2.versionNumber").value(2))
                .andExpect(jsonPath("$.data.differences").exists());
    }

    @Test
    @DisplayName("测试4: 版本回滚")
    public void testRollback() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/timemachine/rollback/{versionId}", testVersionId1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.versionType").value(VersionType.ROLLBACK.name().toLowerCase()))
                .andExpect(jsonPath("$.data.parentVersionId").value(testVersionId1.toString()))
                .andReturn();

        // 验证回滚后版本时间线增加了一个ROLLBACK类型的版本
        mockMvc.perform(get("/v1/timemachine/timeline/{taskId}", testTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].versionType").value(VersionType.ROLLBACK.name().toLowerCase()));
    }

    @Test
    @DisplayName("测试5: 删除版本")
    public void testDeleteVersion() throws Exception {
        // 删除版本2
        mockMvc.perform(delete("/v1/timemachine/version/{versionId}", testVersionId2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("版本已删除"));

        // 验证版本2已被删除
        mockMvc.perform(get("/v1/timemachine/timeline/{taskId}", testTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].versionNumber").value(1));
    }

    @Test
    @DisplayName("测试6: 获取不存在的版本（错误处理）")
    public void testGetNonExistentVersion() throws Exception {
        // 使用无效的UUID格式会触发IllegalArgumentException，返回PARAM_ERROR (1001)
        Long nonExistentId = 99999L;
        mockMvc.perform(get("/v1/timemachine/version/{versionId}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001")) // PARAM_ERROR
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("测试7: 获取空任务的时间线")
    public void testGetTimelineForEmptyTask() throws Exception {
        UUID emptyTaskId = UUID.randomUUID();

        // 创建空任务（设置所有必需字段）
        GenerationTaskEntity emptyTask = new GenerationTaskEntity();
        emptyTask.setId(emptyTaskId);
        emptyTask.setTenantId(testTenantId);
        emptyTask.setUserId(testUserId);
        emptyTask.setTaskName("空任务");
        emptyTask.setUserRequirement("空任务测试");
        emptyTask.setStatus("pending");
        emptyTask.setCreatedAt(Instant.now());
        emptyTask.setUpdatedAt(Instant.now());
        taskMapper.insert(emptyTask);

        // 验证返回空列表
        mockMvc.perform(get("/v1/timemachine/timeline/{taskId}", emptyTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
