package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 创建项目请求DTO
 * 用于接收创建项目时提交的数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    /**
     * 项目名称
     * 必填，长度1-200字符
     */
    @NotBlank(message = "项目名称不能为空")
    @Size(min = 1, max = 200, message = "项目名称长度必须在1-200字符之间")
    private String name;

    /**
     * 项目描述
     * 可选，长度最多2000字符
     */
    @Size(max = 2000, message = "项目描述长度不能超过2000字符")
    private String description;

    /**
     * 封面图片URL
     * 可选
     */
    private String coverImageUrl;

    /**
     * 关联的AppSpec ID
     * 可选，用于关联已存在的AppSpec
     */
    private String appSpecId;

    /**
     * 可见性：private/public/unlisted
     * 可选，默认为private
     */
    private String visibility;

    /**
     * 标签列表
     * 可选，用于分类和搜索
     */
    private List<String> tags;

    /**
     * 年龄分组：elementary/middle_school/high_school/university
     * 可选
     */
    private String ageGroup;

    /**
     * 元数据
     * 可选，用于存储额外信息
     */
    private Map<String, Object> metadata;
}
