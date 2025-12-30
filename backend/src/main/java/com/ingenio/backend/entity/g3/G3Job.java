package com.ingenio.backend.entity.g3;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "g3_job", autoResultMap = true)
public class G3Job {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String appSpecId;

    private String status; // QUEUED, PLANNING, CODING, TESTING, COMPLETED, FAILED

    private Integer currentRound;

    private String contractYaml; // OpenAPI Spec

    private String dbSchemaSql;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<G3Log> logs;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
