-- 添加 G3 任务的需求分析上下文字段
-- 用于存储 Step 1-6 的压缩分析结果，供 G3 Agent 理解需求上下文

ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS analysis_context_json JSONB;

-- 添加注释
COMMENT ON COLUMN g3_jobs.analysis_context_json IS 'Step 1-6 分析上下文摘要（JSON 格式），来源：NLRequirementAnalyzer.buildCompressedAnalysisContext()';
