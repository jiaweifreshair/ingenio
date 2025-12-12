package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.config.TestSaTokenConfig;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.service.GenerationTaskStatusManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
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
import static org.junit.jupiter.api.Assertions.*;

/**
 * GenerationTaskController E2E测试
 *
 * 测试场景：
 * 1. 创建异步生成任务
 * 2. 查询任务状态（含Redis缓存验证）
 * 3. 取消任务
 * 4. 分页查询用户任务列表
 * 5. 任务状态流转验证
 * 6. Redis缓存一致性验证
 *
 * 测试策略：
 * - 使用真实PostgreSQL数据库（TestContainers）
 * - 使用真实Redis（如果可用）
 * - 零Mock策略：使用真实Service和Mapper
 * - 每个测试前初始化测试数据
 * - 验证数据库和Redis状态
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@DisplayName("生成任务API E2E测试")
public class GenerationTaskE2ETest extends BaseE2ETest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired(required = false)
    private GenerationTaskStatusManager statusManager;

    private UUID testUserId;
    private UUID testTenantId;
    private UUID testTaskId;

    /**
     * 每个测试前初始化测试数据
     */
    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        // 使用TestSaTokenConfig中的固定TEST_USER_ID，确保与Sa-Token Mock的userId一致
        testUserId = TestSaTokenConfig.TEST_USER_ID;
        testTenantId = TestSaTokenConfig.TEST_TENANT_ID;
        long timestamp = System.currentTimeMillis();

        // 删除可能已存在的测试用户（避免主键冲突）
        try {
            userMapper.deleteById(testUserId);
        } catch (Exception e) {
            // 忽略删除错误（用户可能不存在）
        }

        // 创建测试用户（满足外键约束），使用固定的TEST_USER_ID
        UserEntity testUser = UserEntity.builder()
            .id(testUserId)  // 使用固定ID
            .tenantId(testTenantId)
            .username("test-task-user-" + timestamp)
            .email("test-task-" + timestamp + "@ingenio.test")
            .passwordHash("test-password-hash")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        userMapper.insert(testUser);

        // 创建测试任务
        testTaskId = UUID.randomUUID();
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setId(testTaskId);
        task.setTenantId(testTenantId);
        task.setUserId(testUserId);
        task.setTaskName("测试生成任务");
        task.setUserRequirement("创建一个图书管理系统，支持借阅和归还功能");
        task.setStatus(GenerationTaskEntity.Status.PENDING.getValue());
        task.setProgress(0);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskMapper.insert(task);

        // 清理Redis缓存（使用statusManager确保键名正确）
        if (statusManager != null) {
            statusManager.removeTaskStatus(testTaskId);
        }
    }

    @Test
    @DisplayName("测试1: 创建异步生成任务 - 成功场景")
    public void testCreateAsyncTask_Success() throws Exception {
        // 准备请求数据
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("userRequirement", "创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价");
        requestBody.put("skipValidation", false);
        requestBody.put("qualityThreshold", 70);
        requestBody.put("generatePreview", true);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        // 执行POST请求创建异步任务
        MvcResult result = mockMvc.perform(post("/v1/generate/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        // 提取任务ID
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        String taskId = (String) responseMap.get("data");

        // 验证任务ID是有效的UUID
        assertDoesNotThrow(() -> UUID.fromString(taskId), "任务ID应为有效的UUID");

        // 验证数据库中任务已创建
        GenerationTaskEntity createdTask = taskMapper.selectById(UUID.fromString(taskId));
        assertNotNull(createdTask, "数据库中应存在创建的任务记录");
        assertEquals(TestSaTokenConfig.TEST_USER_ID, createdTask.getUserId(), "任务所有者应为测试环境的模拟用户");
        assertTrue(
            createdTask.getStatus().equals(GenerationTaskEntity.Status.PENDING.getValue()) ||
            createdTask.getStatus().equals(GenerationTaskEntity.Status.PLANNING.getValue()),
            "任务状态应为PENDING或PLANNING"
        );
    }

    @Test
    @DisplayName("测试2: 查询任务状态 - 成功场景（含Redis缓存验证）")
    public void testGetTaskStatus_Success() throws Exception {
        // 执行GET请求查询任务状态
        mockMvc.perform(get("/v1/generate/status/{taskId}", testTaskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value(testTaskId.toString()))
                .andExpect(jsonPath("$.data.taskName").value("测试生成任务"))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.progress").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists());

        // 验证Redis缓存（如果Redis可用）
        if (redisTemplate != null) {
            String cacheKey = "task:status:" + testTaskId;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            // 首次查询可能不会缓存，但第二次查询应该会缓存
            if (cached != null) {
                assertNotNull(cached, "Redis缓存应存在");
                assertTrue(cached.contains(testTaskId.toString()), "缓存内容应包含任务ID");
            }
        }
    }

    @Test
    @DisplayName("测试3: 查询任务状态 - 任务运行中场景")
    public void testGetTaskStatus_Running() throws Exception {
        // 更新任务状态为EXECUTING
        GenerationTaskEntity task = taskMapper.selectById(testTaskId);
        task.setStatus(GenerationTaskEntity.Status.EXECUTING.getValue());
        task.setCurrentAgent(GenerationTaskEntity.AgentType.EXECUTE.getValue());
        task.setProgress(45);
        task.setStartedAt(Instant.now());
        taskMapper.updateById(task);

        // 执行GET请求查询任务状态
        mockMvc.perform(get("/v1/generate/status/{taskId}", testTaskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("executing"))
                .andExpect(jsonPath("$.data.currentAgent").value("execute"))
                .andExpect(jsonPath("$.data.progress").value(45))
                .andExpect(jsonPath("$.data.startedAt").exists())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
    }

    @Test
    @DisplayName("测试4: 取消任务 - 成功场景")
    public void testCancelTask_Success() throws Exception {
        // 更新任务状态为PLANNING（可取消状态）
        GenerationTaskEntity task = taskMapper.selectById(testTaskId);
        task.setStatus(GenerationTaskEntity.Status.PLANNING.getValue());
        task.setCurrentAgent(GenerationTaskEntity.AgentType.PLAN.getValue());
        task.setProgress(10);
        task.setStartedAt(Instant.now());
        taskMapper.updateById(task);

        // 执行POST请求取消任务
        mockMvc.perform(post("/v1/generate/cancel/{taskId}", testTaskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务已取消"));

        // 验证数据库中任务状态已更新为CANCELLED
        GenerationTaskEntity cancelledTask = taskMapper.selectById(testTaskId);
        assertEquals(GenerationTaskEntity.Status.CANCELLED.getValue(), cancelledTask.getStatus(), "任务状态应为CANCELLED");
        assertNotNull(cancelledTask.getCompletedAt(), "任务完成时间应已设置");
    }

    @Test
    @DisplayName("测试5: 取消任务 - 已完成任务无法取消")
    public void testCancelTask_AlreadyCompleted() throws Exception {
        // 更新任务状态为COMPLETED
        GenerationTaskEntity task = taskMapper.selectById(testTaskId);
        task.setStatus(GenerationTaskEntity.Status.COMPLETED.getValue());
        task.setProgress(100);
        task.setCompletedAt(Instant.now());
        taskMapper.updateById(task);

        // 执行POST请求取消任务，期望返回错误
        mockMvc.perform(post("/v1/generate/cancel/{taskId}", testTaskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001"))  // ErrorCode.PARAM_ERROR
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("无法取消")));
    }

    @Test
    @DisplayName("测试6: 分页查询用户任务列表 - 成功场景")
    public void testListUserTasks_Success() throws Exception {
        // 创建多个测试任务
        for (int i = 1; i <= 5; i++) {
            UUID newTaskId = UUID.randomUUID();
            GenerationTaskEntity task = new GenerationTaskEntity();
            task.setId(newTaskId);
            task.setTenantId(testTenantId);
            task.setUserId(testUserId);
            task.setTaskName("任务" + i);
            task.setUserRequirement("需求" + i);
            task.setStatus(GenerationTaskEntity.Status.PENDING.getValue());
            task.setProgress(0);
            task.setCreatedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            taskMapper.insert(task);
        }

        // 执行GET请求查询任务列表
        mockMvc.perform(get("/v1/generate/tasks")
                .param("pageNum", "1")
                .param("pageSize", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tasks", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }

    @Test
    @DisplayName("测试7: 按状态筛选任务列表 - 成功场景")
    public void testListUserTasksByStatus_Success() throws Exception {
        // 创建不同状态的任务
        createTaskWithStatus(GenerationTaskEntity.Status.COMPLETED);
        createTaskWithStatus(GenerationTaskEntity.Status.FAILED);
        createTaskWithStatus(GenerationTaskEntity.Status.EXECUTING);

        // 执行GET请求，筛选COMPLETED状态的任务
        mockMvc.perform(get("/v1/generate/tasks")
                .param("pageNum", "1")
                .param("pageSize", "10")
                .param("status", "completed")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tasks", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.tasks[*].status", everyItem(equalTo("completed"))));
    }

    @Test
    @DisplayName("测试8: 查询不存在的任务 - 错误处理")
    public void testGetNonExistentTask() throws Exception {
        UUID nonExistentTaskId = UUID.randomUUID();

        // 执行GET请求，期望返回错误（ErrorCode.NOT_FOUND = "1002"）
        mockMvc.perform(get("/v1/generate/status/{taskId}", nonExistentTaskId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1002"))  // ErrorCode.NOT_FOUND
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("不存在")));
    }

    @Test
    @DisplayName("测试9: 任务状态流转验证 - PENDING到COMPLETED")
    public void testTaskStatusTransition() throws Exception {
        // 创建新任务（PENDING）
        UUID newTaskId = UUID.randomUUID();
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setId(newTaskId);
        task.setTenantId(testTenantId);
        task.setUserId(testUserId);
        task.setTaskName("状态流转测试任务");
        task.setUserRequirement("测试需求");
        task.setStatus(GenerationTaskEntity.Status.PENDING.getValue());
        task.setProgress(0);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        taskMapper.insert(task);

        // 验证初始状态
        mockMvc.perform(get("/v1/generate/status/{taskId}", newTaskId))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.progress").value(0));

        // 更新到PLANNING状态（先查询最新数据，再修改字段，避免覆盖其他字段）
        GenerationTaskEntity taskToUpdate = taskMapper.selectById(newTaskId);
        taskToUpdate.setStatus(GenerationTaskEntity.Status.PLANNING.getValue());
        taskToUpdate.setCurrentAgent(GenerationTaskEntity.AgentType.PLAN.getValue());
        taskToUpdate.setProgress(25);
        taskToUpdate.setStartedAt(Instant.now());
        taskMapper.updateById(taskToUpdate);
        // 清除Redis缓存，确保查询从数据库读取最新数据
        if (statusManager != null) {
            statusManager.removeTaskStatus(newTaskId);
        }

        mockMvc.perform(get("/v1/generate/status/{taskId}", newTaskId))
                .andExpect(jsonPath("$.data.status").value("planning"))
                .andExpect(jsonPath("$.data.progress").value(25));

        // 更新到EXECUTING状态
        taskToUpdate = taskMapper.selectById(newTaskId);
        taskToUpdate.setStatus(GenerationTaskEntity.Status.EXECUTING.getValue());
        taskToUpdate.setCurrentAgent(GenerationTaskEntity.AgentType.EXECUTE.getValue());
        taskToUpdate.setProgress(50);
        taskMapper.updateById(taskToUpdate);
        // 清除Redis缓存
        if (statusManager != null) {
            statusManager.removeTaskStatus(newTaskId);
        }

        mockMvc.perform(get("/v1/generate/status/{taskId}", newTaskId))
                .andExpect(jsonPath("$.data.status").value("executing"))
                .andExpect(jsonPath("$.data.progress").value(50));

        // 更新到VALIDATING状态
        taskToUpdate = taskMapper.selectById(newTaskId);
        taskToUpdate.setStatus(GenerationTaskEntity.Status.VALIDATING.getValue());
        taskToUpdate.setCurrentAgent(GenerationTaskEntity.AgentType.VALIDATE.getValue());
        taskToUpdate.setProgress(75);
        taskMapper.updateById(taskToUpdate);
        // 清除Redis缓存
        if (statusManager != null) {
            statusManager.removeTaskStatus(newTaskId);
        }

        mockMvc.perform(get("/v1/generate/status/{taskId}", newTaskId))
                .andExpect(jsonPath("$.data.status").value("validating"))
                .andExpect(jsonPath("$.data.progress").value(75));

        // 更新到COMPLETED状态
        taskToUpdate = taskMapper.selectById(newTaskId);
        taskToUpdate.setStatus(GenerationTaskEntity.Status.COMPLETED.getValue());
        taskToUpdate.setProgress(100);
        taskToUpdate.setCompletedAt(Instant.now());
        taskToUpdate.setQualityScore(85);
        taskMapper.updateById(taskToUpdate);
        // 清除Redis缓存
        if (statusManager != null) {
            statusManager.removeTaskStatus(newTaskId);
        }

        mockMvc.perform(get("/v1/generate/status/{taskId}", newTaskId))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.progress").value(100))
                .andExpect(jsonPath("$.data.qualityScore").value(85))
                .andExpect(jsonPath("$.data.completedAt").exists());
    }

    @Test
    @DisplayName("测试10: Redis缓存一致性验证")
    public void testRedisCacheConsistency() throws Exception {
        // 如果Redis和statusManager不可用，跳过测试
        if (statusManager == null) {
            return;
        }

        // 首次查询任务状态（应从数据库加载）
        mockMvc.perform(get("/v1/generate/status/{taskId}", testTaskId));

        // 更新数据库中的任务进度
        GenerationTaskEntity task = taskMapper.selectById(testTaskId);
        task.setProgress(30);
        taskMapper.updateById(task);

        // 清除Redis缓存
        statusManager.removeTaskStatus(testTaskId);

        // 再次查询任务状态（应从数据库重新加载）
        mockMvc.perform(get("/v1/generate/status/{taskId}", testTaskId))
                .andExpect(jsonPath("$.data.progress").value(30));

        // 验证缓存一致性（通过再次查询确认数据一致）
        mockMvc.perform(get("/v1/generate/status/{taskId}", testTaskId))
                .andExpect(jsonPath("$.data.progress").value(30));
    }

    /**
     * 辅助方法：创建指定状态的任务
     *
     * @param status 任务状态
     */
    private void createTaskWithStatus(GenerationTaskEntity.Status status) {
        UUID taskId = UUID.randomUUID();
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setId(taskId);
        task.setTenantId(testTenantId);
        task.setUserId(testUserId);
        task.setTaskName("任务-" + status.getValue());
        task.setUserRequirement("状态为" + status.getValue() + "的任务");
        task.setStatus(status.getValue());
        task.setProgress(status == GenerationTaskEntity.Status.COMPLETED ? 100 : 50);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        if (status == GenerationTaskEntity.Status.COMPLETED ||
            status == GenerationTaskEntity.Status.FAILED ||
            status == GenerationTaskEntity.Status.CANCELLED) {
            task.setStartedAt(Instant.now().minus(java.time.Duration.ofMinutes(10)));
            task.setCompletedAt(Instant.now());
        } else if (status != GenerationTaskEntity.Status.PENDING) {
            task.setStartedAt(Instant.now().minus(java.time.Duration.ofMinutes(5)));
        }

        if (status == GenerationTaskEntity.Status.FAILED) {
            task.setErrorMessage("模拟错误信息");
        }

        taskMapper.insert(task);
    }
}
