import { AppSpec, ValidationResult } from './appspec.types';
export interface PlanAgentInput {
    requirement: string;
    context: PlanContext;
}
export interface PlanContext {
    tenantId: string;
    userId: string;
    existingModels?: string[];
    projectType?: ProjectType;
    constraints?: string[];
    preferences?: UserPreferences;
}
export type ProjectType = 'form' | 'dashboard' | 'workflow' | 'marketplace' | 'social' | 'custom';
export interface UserPreferences {
    uiStyle?: 'minimal' | 'modern' | 'classic';
    complexity?: 'simple' | 'moderate' | 'complex';
    mobileFirst?: boolean;
}
export interface PlanAgentOutput {
    modules: Module[];
    flows: FlowPlan[];
    constraints: ConstraintPlan[];
    dependencies: Dependency[];
    estimatedComplexity: ComplexityLevel;
    recommendations?: string[];
}
export interface Module {
    id: string;
    name: string;
    description: string;
    type: ModuleType;
    dependencies: string[];
    priority: 'high' | 'medium' | 'low';
}
export type ModuleType = 'page' | 'component' | 'service' | 'data' | 'auth' | 'integration';
export interface FlowPlan {
    id: string;
    name: string;
    description: string;
    triggerType: 'user' | 'system' | 'scheduled';
    steps: FlowStep[];
}
export interface FlowStep {
    id: string;
    action: string;
    target: string;
    condition?: string;
}
export interface ConstraintPlan {
    type: string;
    description: string;
    affectedModules: string[];
}
export interface Dependency {
    from: string;
    to: string;
    type: 'requires' | 'extends' | 'uses';
    optional: boolean;
}
export type ComplexityLevel = 'low' | 'medium' | 'high';
export interface ExecuteAgentInput {
    plan: PlanAgentOutput;
    modelProvider: ModelProvider;
    context: ExecuteContext;
}
export interface ExecuteContext {
    tenantId: string;
    templateId?: string;
    existingSpec?: Partial<AppSpec>;
    generationMode: 'full' | 'incremental';
}
export type ModelProvider = 'openai' | 'anthropic' | 'local' | 'custom';
export interface ExecuteAgentOutput {
    appSpec: AppSpec;
    generationMetadata: GenerationMetadata;
}
export interface GenerationMetadata {
    modelUsed: string;
    tokensUsed: number;
    latencyMs: number;
    confidence: number;
    warnings?: string[];
}
export interface ValidateAgentInput {
    appSpec: AppSpec;
    validationRules?: ValidationRuleConfig[];
    strictMode?: boolean;
}
export interface ValidationRuleConfig {
    type: string;
    enabled: boolean;
    severity: 'error' | 'warning';
    config?: Record<string, any>;
}
export interface ValidateAgentOutput extends ValidationResult {
    timestamp: string;
    validatorVersion: string;
}
export interface AgentResponse<T> {
    success: boolean;
    data?: T;
    error?: AgentError;
    metadata: ResponseMetadata;
}
export interface AgentError {
    code: string;
    message: string;
    details?: any;
    recoverable: boolean;
}
export interface ResponseMetadata {
    requestId: string;
    agentType: 'plan' | 'execute' | 'validate';
    timestamp: string;
    latencyMs: number;
}
export interface ModelConfig {
    provider: ModelProvider;
    model: string;
    apiKey?: string;
    baseUrl?: string;
    temperature?: number;
    maxTokens?: number;
    timeout?: number;
}
export interface ChatMessage {
    role: 'system' | 'user' | 'assistant';
    content: string;
}
export interface ModelResponse {
    content: string;
    usage: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
    };
    finishReason: 'stop' | 'length' | 'error';
}
export interface AgentExecutionLog {
    id: string;
    requestId: string;
    tenantId: string;
    agentType: 'plan' | 'execute' | 'validate';
    status: ExecutionStatus;
    inputData?: any;
    outputData?: any;
    errorMessage?: string;
    latencyMs: number;
    modelProvider?: string;
    modelName?: string;
    tokenUsage?: {
        prompt: number;
        completion: number;
        total: number;
    };
    createdAt: string;
}
export type ExecutionStatus = 'success' | 'failed' | 'timeout' | 'cancelled';
export interface BatchGenerateRequest {
    requirements: string[];
    context: PlanContext;
    parallel?: boolean;
}
export interface BatchGenerateResponse {
    results: BatchGenerateResult[];
    summary: BatchSummary;
}
export interface BatchGenerateResult {
    index: number;
    success: boolean;
    appSpec?: AppSpec;
    error?: string;
}
export interface BatchSummary {
    total: number;
    succeeded: number;
    failed: number;
    totalLatencyMs: number;
    averageLatencyMs: number;
}
