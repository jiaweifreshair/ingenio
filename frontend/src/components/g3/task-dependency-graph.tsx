"use client";

import { cn } from "@/lib/utils";

/**
 * G3 任务依赖图（轻量展示）
 *
 * 说明：
 * - 这里不引入重型图形库，使用简单的“节点 + 箭头”展示核心生成顺序
 * - 依赖关系来自实施方案：Entity → Mapper → Service → Controller
 */
export function G3TaskDependencyGraph({
  completedPhases,
  className,
}: {
  /** 已完成阶段编号（来自 task_plan.md 的勾选解析） */
  completedPhases: number[];
  className?: string;
}) {
  const done = (phase: number) => completedPhases.includes(phase);

  const nodes: Array<{ label: string; phase: number }> = [
    { label: "Entity", phase: 2 },
    { label: "Mapper", phase: 3 },
    { label: "Service", phase: 4 },
    { label: "Controller", phase: 5 },
  ];

  return (
    <div
      className={cn(
        "rounded-xl border border-white/[0.08] bg-white/[0.02] p-4",
        className
      )}
    >
      <div className="text-sm font-semibold text-white/80 mb-3">
        生成依赖顺序（DAG）
      </div>

      <div className="flex items-center gap-3 overflow-x-auto pb-1">
        {nodes.map((n, idx) => (
          <div key={n.label} className="flex items-center gap-3 shrink-0">
            <div
              className={cn(
                "px-3 py-1.5 rounded-lg border text-xs font-mono",
                done(n.phase)
                  ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-200"
                  : "bg-white/[0.03] border-white/[0.10] text-white/70"
              )}
              title={`阶段${n.phase}`}
            >
              {n.label}
            </div>
            {idx < nodes.length - 1 && (
              <div className="text-white/25 select-none" aria-hidden="true">
                →
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="mt-3 text-xs text-white/45">
        提示：当后续阶段依赖前置类时，建议先完成前置阶段以减少 import/类名缺失。
      </div>
    </div>
  );
}

