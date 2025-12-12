package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.VersionType;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import com.ingenio.backend.service.TimeMachineAdvancedService;
import com.ingenio.backend.service.TimeMachineAdvancedService.*;
import com.ingenio.backend.service.VersionSnapshotService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TimeMachine高级功能E2E测试
 *
 * <p>测试覆盖V2.0新增功能：</p>
 * <ul>
 *   <li>版本标签管理（添加/移除/按标签查询）</li>
 *   <li>分支创建（从任意版本创建新分支）</li>
 *   <li>代码级别对比</li>
 *   <li>版本搜索</li>
 *   <li>批量操作</li>
 *   <li>版本导出/导入</li>
 *   <li>版本统计</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-30 E2E测试覆盖
 */
@DisplayName("TimeMachine高级功能E2E测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimeMachineAdvancedE2ETest extends BaseE2ETest {

    /** 测试租户ID */
    protected static final UUID TEST_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** 测试用户ID */
    protected static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TimeMachineAdvancedService advancedService;

    @Autowired
    private VersionSnapshotService snapshotService;

    @Autowired
    private GenerationTaskMapper taskMapper;

    @Autowired
    private GenerationVersionMapper versionMapper;

    private static UUID testTaskId;
    private static UUID testVersionId1;
    private static UUID testVersionId2;
    private static UUID testVersionId3;

    @BeforeAll
    static void initTestData(@Autowired GenerationTaskMapper taskMapper,
                             @Autowired VersionSnapshotService snapshotService) {
        // 创建测试任务
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setId(UUID.randomUUID());
        task.setTenantId(TEST_TENANT_ID);
        task.setUserId(TEST_USER_ID);
        task.setTaskName("TimeMachine测试任务");
        task.setUserRequirement("E2E测试用任务");
        task.setStatus("completed");
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        task.setMetadata(Map.of("test", true));
        taskMapper.insert(task);
        testTaskId = task.getId();

        // 创建3个测试版本（用于diff测试）
        Map<String, Object> snapshot1 = new HashMap<>();
        snapshot1.put("entities", Map.of(
                "User.java", "public class User { private String name; }",
                "Product.java", "public class Product { private String title; }"
        ));
        snapshot1.put("entity_count", 2);

        GenerationVersionEntity version1 = snapshotService.createSnapshot(
                testTaskId, TEST_TENANT_ID, VersionType.PLAN, snapshot1);
        testVersionId1 = version1.getId();

        // 版本2：修改了User.java，新增了Order.java
        Map<String, Object> snapshot2 = new HashMap<>();
        snapshot2.put("entities", Map.of(
                "User.java", "public class User { private String name; private String email; }",
                "Product.java", "public class Product { private String title; }",
                "Order.java", "public class Order { private Long id; }"
        ));
        snapshot2.put("entity_count", 3);
        snapshot2.put("table_count", 3);

        GenerationVersionEntity version2 = snapshotService.createSnapshot(
                testTaskId, TEST_TENANT_ID, VersionType.SCHEMA, snapshot2);
        testVersionId2 = version2.getId();

        // 版本3：删除了Product.java
        Map<String, Object> snapshot3 = new HashMap<>();
        snapshot3.put("entities", Map.of(
                "User.java", "public class User { private String name; private String email; }",
                "Order.java", "public class Order { private Long id; private BigDecimal amount; }"
        ));
        snapshot3.put("entity_count", 2);
        snapshot3.put("file_count", 10);

        GenerationVersionEntity version3 = snapshotService.createSnapshot(
                testTaskId, TEST_TENANT_ID, VersionType.CODE, snapshot3);
        testVersionId3 = version3.getId();
    }

    // ==================== 1. 版本标签管理测试 ====================

    @Test
    @Order(1)
    @DisplayName("标签管理: 添加版本标签")
    void testAddTag() throws Exception {
        mockMvc.perform(post("/v1/timemachine/version/{versionId}/tag", testVersionId1)
                        .param("tag", "milestone")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testVersionId1.toString()));

        // 验证标签已添加
        GenerationVersionEntity version = advancedService.addTag(testVersionId1, "released");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) version.getSnapshot().get("tags");
        assertThat(tags).contains("milestone", "released");
    }

    @Test
    @Order(2)
    @DisplayName("标签管理: 移除版本标签")
    void testRemoveTag() throws Exception {
        // 先添加标签
        advancedService.addTag(testVersionId2, "temp-tag");

        // 通过API移除
        mockMvc.perform(delete("/v1/timemachine/version/{versionId}/tag", testVersionId2)
                        .param("tag", "temp-tag")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证标签已移除
        GenerationVersionEntity version = versionMapper.selectById(testVersionId2);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) version.getSnapshot().getOrDefault("tags", Collections.emptyList());
        assertThat(tags).doesNotContain("temp-tag");
    }

    @Test
    @Order(3)
    @DisplayName("标签管理: 按标签查询版本")
    void testGetVersionsByTag() throws Exception {
        // 给version1添加"important"标签
        advancedService.addTag(testVersionId1, "important");

        mockMvc.perform(get("/v1/timemachine/task/{taskId}/versions/by-tag", testTaskId)
                        .param("tag", "important")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(4)
    @DisplayName("标签管理: 批量添加标签")
    void testBatchAddTag() throws Exception {
        List<String> versionIds = List.of(testVersionId1.toString(), testVersionId2.toString());

        mockMvc.perform(post("/v1/timemachine/versions/batch-tag")
                        .param("tag", "batch-tagged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(2));

        // 验证两个版本都有标签
        List<GenerationVersionEntity> tagged = advancedService.getVersionsByTag(testTaskId, "batch-tagged");
        assertThat(tagged).hasSize(2);
    }

    // ==================== 2. 分支创建测试 ====================

    @Test
    @Order(10)
    @DisplayName("分支创建: 从版本创建新分支")
    void testCreateBranch() throws Exception {
        mockMvc.perform(post("/v1/timemachine/version/{versionId}/branch", testVersionId1)
                        .param("branchName", "feature-experiment")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branchName").value("feature-experiment"))
                .andExpect(jsonPath("$.data.sourceVersionId").value(testVersionId1.toString()))
                .andExpect(jsonPath("$.data.branchTaskId").exists())
                .andExpect(jsonPath("$.data.branchVersionId").exists());
    }

    @Test
    @Order(11)
    @DisplayName("分支创建: 验证分支独立性")
    void testBranchIndependence() {
        // 创建分支
        BranchResult branch = advancedService.createBranch(testVersionId2, "hotfix-branch");

        // 验证分支任务存在
        GenerationTaskEntity branchTask = taskMapper.selectById(branch.getBranchTaskId());
        assertThat(branchTask).isNotNull();
        assertThat(branchTask.getMetadata()).isNotNull();
        assertThat(branchTask.getMetadata().get("branch_name")).isEqualTo("hotfix-branch");
        assertThat(branchTask.getMetadata().get("source_version_id")).isEqualTo(testVersionId2.toString());

        // 验证分支版本包含源信息
        GenerationVersionEntity branchVersion = versionMapper.selectById(branch.getBranchVersionId());
        assertThat(branchVersion).isNotNull();
        assertThat(branchVersion.getSnapshot()).isNotNull();
        assertThat(branchVersion.getSnapshot().get("branch_name")).isEqualTo("hotfix-branch");
    }

    // ==================== 3. 代码级别对比测试 ====================

    @Test
    @Order(20)
    @DisplayName("代码对比: 获取代码级别差异 - 新增文件场景")
    void testCodeLevelDiff_AddedFiles() throws Exception {
        mockMvc.perform(get("/v1/timemachine/code-diff")
                        .param("version1", testVersionId1.toString())
                        .param("version2", testVersionId2.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.addedFiles").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.modifiedFiles").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.fileDiffs").isArray());
    }

    @Test
    @Order(21)
    @DisplayName("代码对比: 获取代码级别差异 - 删除文件场景")
    void testCodeLevelDiff_RemovedFiles() {
        CodeLevelDiff diff = advancedService.getCodeLevelDiff(testVersionId2, testVersionId3);

        assertThat(diff).isNotNull();
        assertThat(diff.getVersion1Number()).isLessThan(diff.getVersion2Number());
        // 从version2到version3删除了Product.java
        assertThat(diff.getRemovedFiles()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(22)
    @DisplayName("代码对比: 验证行级统计")
    void testCodeLevelDiff_LineStatistics() {
        CodeLevelDiff diff = advancedService.getCodeLevelDiff(testVersionId1, testVersionId3);

        assertThat(diff).isNotNull();
        assertThat(diff.getTotalLinesAdded()).isGreaterThanOrEqualTo(0);
        assertThat(diff.getTotalLinesRemoved()).isGreaterThanOrEqualTo(0);

        // 验证文件差异详情
        assertThat(diff.getFileDiffs()).isNotEmpty();
        for (FileDiff fileDiff : diff.getFileDiffs()) {
            assertThat(fileDiff.getFilePath()).isNotEmpty();
            assertThat(fileDiff.getChangeType()).isIn("added", "removed", "modified");
        }
    }

    // ==================== 4. 版本搜索测试 ====================

    @Test
    @Order(30)
    @DisplayName("版本搜索: 按版本类型筛选")
    void testSearchVersions_ByType() throws Exception {
        // 版本类型在数据库中存储为小写
        mockMvc.perform(get("/v1/timemachine/task/{taskId}/search", testTaskId)
                        .param("versionTypes", "plan", "schema")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(31)
    @DisplayName("版本搜索: 按版本号范围筛选")
    void testSearchVersions_ByVersionRange() throws Exception {
        mockMvc.perform(get("/v1/timemachine/task/{taskId}/search", testTaskId)
                        .param("minVersion", "1")
                        .param("maxVersion", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(32)
    @DisplayName("版本搜索: 按标签筛选")
    void testSearchVersions_ByTag() {
        // 确保有标签的版本
        advancedService.addTag(testVersionId1, "search-test-tag");

        VersionSearchParams params = VersionSearchParams.builder()
                .tags(List.of("search-test-tag"))
                .build();

        List<?> results = advancedService.searchVersions(testTaskId, params);
        assertThat(results).isNotEmpty();
    }

    @Test
    @Order(33)
    @DisplayName("版本搜索: 组合条件筛选")
    void testSearchVersions_CombinedFilters() {
        // 版本类型在数据库中存储为小写
        VersionSearchParams params = VersionSearchParams.builder()
                .versionTypes(List.of("plan", "schema", "code"))
                .minVersion(1)
                .maxVersion(10)
                .build();

        List<?> results = advancedService.searchVersions(testTaskId, params);
        assertThat(results).isNotEmpty();
    }

    // ==================== 5. 批量操作测试 ====================

    @Test
    @Order(40)
    @DisplayName("批量操作: 删除指定版本号之后的版本")
    void testDeleteVersionsAfter() {
        // 创建额外的测试任务和版本（避免影响其他测试）
        GenerationTaskEntity tempTask = new GenerationTaskEntity();
        tempTask.setId(UUID.randomUUID());
        tempTask.setTenantId(TEST_TENANT_ID);
        tempTask.setUserId(TEST_USER_ID);
        tempTask.setTaskName("批量删除测试任务");
        tempTask.setUserRequirement("测试删除版本功能");
        tempTask.setStatus("executing");
        tempTask.setCreatedAt(Instant.now());
        tempTask.setUpdatedAt(Instant.now());
        taskMapper.insert(tempTask);

        // 创建5个版本
        for (int i = 0; i < 5; i++) {
            snapshotService.createSnapshot(tempTask.getId(), TEST_TENANT_ID,
                    VersionType.PLAN, Map.of("version", i + 1));
        }

        // 删除版本号>2的所有版本
        int deletedCount = advancedService.deleteVersionsAfter(tempTask.getId(), 2);

        assertThat(deletedCount).isEqualTo(3); // 应该删除版本3、4、5
    }

    // ==================== 6. 版本导出/导入测试 ====================

    @Test
    @Order(50)
    @DisplayName("导出导入: 导出版本快照")
    void testExportVersion() throws Exception {
        mockMvc.perform(get("/v1/timemachine/version/{versionId}/export", testVersionId1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionId").value(testVersionId1.toString()))
                .andExpect(jsonPath("$.data.exportVersion").value("2.0"))
                .andExpect(jsonPath("$.data.snapshot").exists())
                .andExpect(jsonPath("$.data.exportedAt").exists());
    }

    @Test
    @Order(51)
    @DisplayName("导出导入: 导入版本到其他任务")
    void testImportVersion() throws Exception {
        // 创建目标任务
        GenerationTaskEntity targetTask = new GenerationTaskEntity();
        targetTask.setId(UUID.randomUUID());
        targetTask.setTenantId(TEST_TENANT_ID);
        targetTask.setUserId(TEST_USER_ID);
        targetTask.setTaskName("导入目标任务");
        targetTask.setUserRequirement("测试版本导入功能");
        targetTask.setStatus("pending");
        targetTask.setCreatedAt(Instant.now());
        targetTask.setUpdatedAt(Instant.now());
        taskMapper.insert(targetTask);

        // 导出源版本
        VersionExportData exportData = advancedService.exportVersion(testVersionId1);

        // 导入到目标任务
        mockMvc.perform(post("/v1/timemachine/task/{taskId}/import", targetTask.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exportData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").value(targetTask.getId().toString()));
    }

    @Test
    @Order(52)
    @DisplayName("导出导入: 验证导入版本包含源信息")
    void testImportVersion_PreservesSource() {
        // 创建目标任务
        GenerationTaskEntity targetTask = new GenerationTaskEntity();
        targetTask.setId(UUID.randomUUID());
        targetTask.setTenantId(TEST_TENANT_ID);
        targetTask.setUserId(TEST_USER_ID);
        targetTask.setTaskName("导入源信息验证任务");
        targetTask.setUserRequirement("验证导入版本保留源信息");
        targetTask.setStatus("pending");
        targetTask.setCreatedAt(Instant.now());
        targetTask.setUpdatedAt(Instant.now());
        taskMapper.insert(targetTask);

        // 导出并导入
        VersionExportData exportData = advancedService.exportVersion(testVersionId2);
        GenerationVersionEntity imported = advancedService.importVersion(targetTask.getId(), exportData);

        // 验证导入来源信息
        assertThat(imported).isNotNull();
        assertThat(imported.getTaskId()).isEqualTo(targetTask.getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> importedFrom = (Map<String, Object>) imported.getSnapshot().get("imported_from");
        assertThat(importedFrom).isNotNull();
        assertThat(importedFrom.get("source_version_id")).isEqualTo(testVersionId2.toString());
    }

    // ==================== 7. 版本统计测试 ====================

    @Test
    @Order(60)
    @DisplayName("版本统计: 获取任务版本统计信息")
    void testGetStatistics() throws Exception {
        mockMvc.perform(get("/v1/timemachine/task/{taskId}/statistics", testTaskId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalVersions").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.versionsByType").exists())
                .andExpect(jsonPath("$.data.firstVersionTime").exists())
                .andExpect(jsonPath("$.data.lastVersionTime").exists());
    }

    @Test
    @Order(61)
    @DisplayName("版本统计: 验证统计详情")
    void testGetStatistics_Details() {
        VersionStatistics stats = advancedService.getStatistics(testTaskId);

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalVersions()).isGreaterThanOrEqualTo(3);
        assertThat(stats.getVersionsByType()).isNotEmpty();
        assertThat(stats.getFirstVersionTime()).isNotNull();
        assertThat(stats.getLastVersionTime()).isNotNull();
        assertThat(stats.getFirstVersionTime()).isBefore(stats.getLastVersionTime());

        // 验证类型分布（键名为小写）
        assertThat(stats.getVersionsByType()).containsKey("plan");
    }

    @Test
    @Order(62)
    @DisplayName("版本统计: 空任务统计")
    void testGetStatistics_EmptyTask() {
        // 创建空任务
        GenerationTaskEntity emptyTask = new GenerationTaskEntity();
        emptyTask.setId(UUID.randomUUID());
        emptyTask.setTenantId(TEST_TENANT_ID);
        emptyTask.setUserId(TEST_USER_ID);
        emptyTask.setTaskName("空任务统计测试");
        emptyTask.setUserRequirement("测试空任务统计");
        emptyTask.setStatus("pending");
        emptyTask.setCreatedAt(Instant.now());
        emptyTask.setUpdatedAt(Instant.now());
        taskMapper.insert(emptyTask);

        VersionStatistics stats = advancedService.getStatistics(emptyTask.getId());

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalVersions()).isEqualTo(0);
    }

    // ==================== 8. 边界条件测试 ====================

    @Test
    @Order(70)
    @DisplayName("边界条件: 不存在的版本")
    void testNonExistentVersion() throws Exception {
        UUID fakeVersionId = UUID.randomUUID();

        mockMvc.perform(post("/v1/timemachine/version/{versionId}/tag", fakeVersionId)
                        .param("tag", "test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(71)
    @DisplayName("边界条件: 重复添加相同标签")
    void testDuplicateTag() {
        // 添加标签两次
        advancedService.addTag(testVersionId3, "duplicate-test");
        GenerationVersionEntity version = advancedService.addTag(testVersionId3, "duplicate-test");

        // 验证标签只出现一次
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) version.getSnapshot().get("tags");
        long count = tags.stream().filter(t -> t.equals("duplicate-test")).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Order(72)
    @DisplayName("边界条件: 对比相同版本")
    void testCompare_SameVersion() {
        CodeLevelDiff diff = advancedService.getCodeLevelDiff(testVersionId1, testVersionId1);

        assertThat(diff).isNotNull();
        assertThat(diff.getAddedFiles()).isEqualTo(0);
        assertThat(diff.getRemovedFiles()).isEqualTo(0);
        assertThat(diff.getModifiedFiles()).isEqualTo(0);
    }
}
