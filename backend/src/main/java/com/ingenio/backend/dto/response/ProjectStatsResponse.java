package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目统计数据响应DTO
 * 用于Dashboard页面的统计卡片展示
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStatsResponse {

    /**
     * 总应用数
     */
    private Integer totalProjects;

    /**
     * 本月新增应用数
     */
    private Integer monthlyNewProjects;

    /**
     * 生成中的任务数
     */
    private Integer generatingTasks;

    /**
     * 已发布应用数
     */
    private Integer publishedProjects;

    /**
     * 草稿状态应用数
     */
    private Integer draftProjects;

    /**
     * 已归档应用数
     */
    private Integer archivedProjects;
}
