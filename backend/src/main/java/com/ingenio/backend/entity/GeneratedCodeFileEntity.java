package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 生成的代码文件实体
 *
 * 用于存储AI生成的代码文件：
 * - Entity/Mapper/Service/Controller等Java文件
 * - 测试文件
 * - 配置文件
 */
@Data
@TableName("generated_code_files")
public class GeneratedCodeFileEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 租户ID
     */
    private UUID tenantId;

    /**
     * 用户ID
     */
    private UUID userId;

    /**
     * 关联的生成任务ID
     */
    private UUID taskId;

    /**
     * 关联的API ID
     */
    private UUID apiId;

    /**
     * 文件类型：entity/mapper/service/controller/test/config
     */
    private String fileType;

    /**
     * 文件路径（相对路径，如src/main/java/com/example/blog/entity/BlogEntity.java）
     */
    private String filePath;

    /**
     * 文件名（如BlogEntity.java）
     */
    private String fileName;

    /**
     * 文件内容（代码）
     */
    private String content;

    /**
     * 编程语言（java/typescript/kotlin等）
     */
    private String language;

    /**
     * 框架（spring-boot/nestjs/ktor等）
     */
    private String framework;

    /**
     * 文件内容SHA256校验和（用于检测变更）
     */
    private String checksum;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
