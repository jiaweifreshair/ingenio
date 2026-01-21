package com.ingenio.backend.service.g3;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G3任务依赖图
 *
 * 用于表示代码生成任务之间的依赖关系
 *
 * @author Claude
 * @since 2025-01-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class G3TaskDependencyGraph {

    /**
     * 任务节点列表
     */
    @Builder.Default
    private List<G3TaskNode> nodes = new ArrayList<>();

    /**
     * 依赖边列表
     */
    @Builder.Default
    private List<G3TaskEdge> edges = new ArrayList<>();

    /**
     * 添加节点
     */
    public void addNode(G3TaskNode node) {
        if (nodes == null) nodes = new ArrayList<>();
        nodes.add(node);
    }

    /**
     * 添加依赖关系 (source -> target)
     * 表示 target 依赖 source (source 必须先完成)
     */
    public void addDependency(String sourceId, String targetId) {
        if (edges == null) edges = new ArrayList<>();
        edges.add(new G3TaskEdge(sourceId, targetId));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class G3TaskNode {
        /**
         * 任务ID (唯一标识)
         */
        private String id;

        /**
         * 任务类型 (entity/mapper/service/controller/other)
         */
        private String type;

        /**
         * 任务名称 (例如 "Create UserEntity")
         */
        private String name;

        /**
         * 任务描述
         */
        private String description;

        /**
         * 优先级 (数字越小优先级越高)
         */
        private int priority;

        /**
         * 状态 (pending/in_progress/completed/failed)
         */
        @Builder.Default
        private String status = "pending";
        
        /**
         * 相关的实体名称 (用于分组)
         */
        private String relatedEntity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class G3TaskEdge {
        /**
         * 前置任务ID
         */
        private String source;

        /**
         * 后置任务ID
         */
        private String target;
    }
}
