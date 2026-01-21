package com.ingenio.backend.service.g3;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.entity.g3.G3PlanningFileEntity;
import com.ingenio.backend.mapper.g3.G3PlanningFileMapper;
import com.ingenio.backend.service.g3.template.ContextTemplate;
import com.ingenio.backend.service.g3.template.NotesTemplate;
import com.ingenio.backend.service.g3.template.TaskPlanTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * G3规划文件服务
 *
 * G3引擎任务规划增强 - 文件管理业务层
 */
@Service
public class G3PlanningFileService {

    private static final Logger log = LoggerFactory.getLogger(G3PlanningFileService.class);

    @Autowired
    private G3PlanningFileMapper planningFileMapper;

    @Autowired
    private TaskPlanTemplate taskPlanTemplate;

    @Autowired
    private NotesTemplate notesTemplate;

    @Autowired
    private ContextTemplate contextTemplate;

    /**
     * 初始化任务的规划文件
     */
    @Transactional
    public void initializePlanningFiles(
            UUID jobId,
            String projectName,
            String requirement,
            List<String> capabilities,
            String basePackage) {
        log.info("初始化G3任务规划文件: jobId={}, project={}", jobId, projectName);

        // 1. 创建task_plan.md
        String taskPlanContent = taskPlanTemplate.generateInitial(projectName, requirement, capabilities);
        createPlanningFile(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN, taskPlanContent,
                G3PlanningFileEntity.UPDATER_SYSTEM);

        // 2. 创建notes.md
        String notesContent = notesTemplate.generateInitial(projectName);
        createPlanningFile(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES, notesContent,
                G3PlanningFileEntity.UPDATER_SYSTEM);

        // 3. 创建context.md
        String contextContent = contextTemplate.generateInitial(projectName, basePackage);
        createPlanningFile(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT, contextContent,
                G3PlanningFileEntity.UPDATER_SYSTEM);

        log.info("G3任务规划文件初始化完成: jobId={}", jobId);
    }

    /**
     * 创建规划文件
     */
    @Transactional
    public G3PlanningFileEntity createPlanningFile(UUID jobId, String fileType, String content, String updatedBy) {
        log.info("创建规划文件: jobId={}, type={}", jobId, fileType);

        G3PlanningFileEntity entity = G3PlanningFileEntity.builder()
                .id(UUID.randomUUID()) // 手动生成UUID
                .jobId(jobId)
                .fileType(fileType)
                .content(content)
                .version(1)
                .lastUpdatedBy(updatedBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        planningFileMapper.insert(entity);
        log.info("规划文件创建成功: id={}, type={}", entity.getId(), fileType);

        return entity;
    }

    /**
     * 获取任务的所有规划文件
     */
    public List<G3PlanningFileEntity> listByJob(UUID jobId) {
        log.info("查询任务规划文件: jobId={}", jobId);

        LambdaQueryWrapper<G3PlanningFileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(G3PlanningFileEntity::getJobId, jobId)
                .orderByAsc(G3PlanningFileEntity::getFileType);

        List<G3PlanningFileEntity> files = planningFileMapper.selectList(wrapper);
        log.info("任务 {} 共有 {} 个规划文件", jobId, files.size());

        return files;
    }

    /**
     * 获取任务的所有规划文件（Map格式）
     */
    public Map<String, G3PlanningFileEntity> getByJobAsMap(UUID jobId) {
        List<G3PlanningFileEntity> files = listByJob(jobId);
        Map<String, G3PlanningFileEntity> result = new HashMap<>();

        for (G3PlanningFileEntity file : files) {
            result.put(file.getFileType(), file);
        }

        return result;
    }

    /**
     * 获取指定类型的规划文件
     */
    public Optional<G3PlanningFileEntity> getByJobAndType(UUID jobId, String fileType) {
        log.info("查询规划文件: jobId={}, type={}", jobId, fileType);

        LambdaQueryWrapper<G3PlanningFileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(G3PlanningFileEntity::getJobId, jobId)
                .eq(G3PlanningFileEntity::getFileType, fileType);

        G3PlanningFileEntity file = planningFileMapper.selectOne(wrapper);
        return Optional.ofNullable(file);
    }

    /**
     * 获取文件内容
     */
    public String getContent(UUID jobId, String fileType) {
        Optional<G3PlanningFileEntity> fileOpt = getByJobAndType(jobId, fileType);
        return fileOpt.map(G3PlanningFileEntity::getContent).orElse("");
    }

    /**
     * 更新规划文件内容
     */
    @Transactional
    public G3PlanningFileEntity updateContent(UUID jobId, String fileType, String content, String updatedBy) {
        log.info("更新规划文件: jobId={}, type={}, by={}", jobId, fileType, updatedBy);

        Optional<G3PlanningFileEntity> fileOpt = getByJobAndType(jobId, fileType);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("规划文件不存在: jobId=" + jobId + ", type=" + fileType);
        }

        G3PlanningFileEntity file = fileOpt.get();
        file.setContent(content);
        file.setLastUpdatedBy(updatedBy);

        planningFileMapper.updateById(file);
        // version/updatedAt 由数据库触发器统一维护，这里以 DB 最新值为准返回
        G3PlanningFileEntity refreshed = planningFileMapper.selectById(file.getId());
        log.info("规划文件更新成功: id={}, version={}", file.getId(),
                refreshed != null ? refreshed.getVersion() : file.getVersion());

        return refreshed != null ? refreshed : file;
    }

    /**
     * 追加内容到规划文件
     */
    @Transactional
    public G3PlanningFileEntity appendContent(UUID jobId, String fileType, String appendContent, String updatedBy) {
        log.info("追加规划文件内容: jobId={}, type={}", jobId, fileType);

        Optional<G3PlanningFileEntity> fileOpt = getByJobAndType(jobId, fileType);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("规划文件不存在: jobId=" + jobId + ", type=" + fileType);
        }

        G3PlanningFileEntity file = fileOpt.get();
        String newContent = file.getContent() + "\n" + appendContent;

        file.setContent(newContent);
        file.setLastUpdatedBy(updatedBy);

        planningFileMapper.updateById(file);
        // version/updatedAt 由数据库触发器统一维护，这里以 DB 最新值为准返回
        G3PlanningFileEntity refreshed = planningFileMapper.selectById(file.getId());
        log.info("规划文件追加成功: id={}, version={}", file.getId(),
                refreshed != null ? refreshed.getVersion() : file.getVersion());

        return refreshed != null ? refreshed : file;
    }

    // ==================== task_plan.md 操作 ====================

    @Transactional
    public void updatePhaseStatus(UUID jobId, int phase, boolean completed, String updatedBy) {
        log.info("更新阶段状态: jobId={}, phase={}, completed={}", jobId, phase, completed);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN);
        String newContent = taskPlanTemplate.updatePhaseStatus(content, phase, completed);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN, newContent, updatedBy);
    }

    @Transactional
    public void appendDecision(UUID jobId, String decision, String reason, String updatedBy) {
        log.info("追加决策记录: jobId={}, decision={}", jobId, decision);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN);
        String newContent = taskPlanTemplate.appendDecision(content, decision, reason);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN, newContent, updatedBy);
    }

    @Transactional
    public void appendError(UUID jobId, String error, String solution, String updatedBy) {
        log.info("追加错误记录: jobId={}, error={}", jobId, error);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN);
        String newContent = taskPlanTemplate.appendError(content, error, solution);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN, newContent, updatedBy);
    }

    @Transactional
    public void updateStatus(UUID jobId, String phase, int progress, String status, String updatedBy) {
        log.info("更新当前状态: jobId={}, phase={}, progress={}%", jobId, phase, progress);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN);
        String newContent = taskPlanTemplate.updateStatus(content, phase, progress, status);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_TASK_PLAN, newContent, updatedBy);
    }

    // ==================== notes.md 操作 ====================

    @Transactional
    public void addEntityDesign(UUID jobId, List<Map<String, Object>> entities, String updatedBy) {
        log.info("添加实体设计: jobId={}, entityCount={}", jobId, entities.size());

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES);
        String newContent = notesTemplate.addEntityDesign(content, entities);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES, newContent, updatedBy);
    }

    @Transactional
    public void addApiDesign(UUID jobId, List<Map<String, Object>> apis, String updatedBy) {
        log.info("添加API设计: jobId={}, apiCount={}", jobId, apis.size());

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES);
        String newContent = notesTemplate.addApiDesign(content, apis);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES, newContent, updatedBy);
    }

    @Transactional
    public void addCapabilityNotes(UUID jobId, String capabilityCode, String notes, String updatedBy) {
        log.info("添加能力集成笔记: jobId={}, capability={}", jobId, capabilityCode);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES);
        String newContent = notesTemplate.addCapabilityNotes(content, capabilityCode, notes);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_NOTES, newContent, updatedBy);
    }

    // ==================== context.md 操作 ====================

    @Transactional
    public void addGeneratedFile(UUID jobId, String filePath, String className, String type, String status,
            String updatedBy) {
        log.info("添加已生成文件: jobId={}, file={}", jobId, filePath);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT);
        String newContent = contextTemplate.addGeneratedFile(content, filePath, className, type, status);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT, newContent, updatedBy);
    }

    @Transactional
    public void updateImportIndex(UUID jobId, String type, List<String> imports, String updatedBy) {
        log.info("更新Import索引: jobId={}, type={}, count={}", jobId, type, imports.size());

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT);
        String newContent = contextTemplate.updateImportIndex(content, type, imports);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT, newContent, updatedBy);
    }

    @Transactional
    public void addClassSignature(UUID jobId, String type, String className, String signature, String updatedBy) {
        log.info("添加类签名: jobId={}, type={}, class={}", jobId, type, className);

        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT);
        String newContent = contextTemplate.addClassSignature(content, type, className, signature);
        updateContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT, newContent, updatedBy);
    }

    public String getCompactContext(UUID jobId) {
        String content = getContent(jobId, G3PlanningFileEntity.FILE_TYPE_CONTEXT);
        return contextTemplate.generateCompactContext(content);
    }

    @Transactional
    public void deleteByJob(UUID jobId) {
        log.info("删除任务规划文件: jobId={}", jobId);

        LambdaQueryWrapper<G3PlanningFileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(G3PlanningFileEntity::getJobId, jobId);

        int deleted = planningFileMapper.delete(wrapper);
        log.info("删除了 {} 个规划文件", deleted);
    }
}
