export interface AppSpec {
    id: string;
    version: string;
    tenantId: string;
    createdAt: string;
    updatedAt: string;
    pages: Page[];
    dataModels: DataModel[];
    flows: Flow[];
    permissions: Permission[];
    constraints: Constraint[];
    testCases: TestCase[];
    customComponents?: CustomComponent[];
    themeVars?: ThemeVariables;
}
export interface Page {
    id: string;
    name: string;
    path: string;
    components: string[];
    layout?: string;
    meta?: PageMeta;
}
export interface PageMeta {
    title?: string;
    description?: string;
    keywords?: string[];
    ogImage?: string;
}
export interface DataModel {
    id: string;
    name: string;
    description?: string;
    fields: Field[];
    indexes?: Index[];
    relations?: Relation[];
}
export interface Field {
    name: string;
    type: FieldType;
    required: boolean;
    unique?: boolean;
    default?: any;
    validation?: ValidationRule;
}
export type FieldType = 'string' | 'number' | 'boolean' | 'date' | 'json' | 'reference' | 'array';
export interface ValidationRule {
    type: 'regex' | 'range' | 'length' | 'custom';
    value: string | number | [number, number];
    message?: string;
}
export interface Index {
    fields: string[];
    unique?: boolean;
    name?: string;
}
export interface Relation {
    type: 'oneToOne' | 'oneToMany' | 'manyToMany';
    target: string;
    foreignKey?: string;
    through?: string;
}
export interface Flow {
    id: string;
    name: string;
    trigger: Trigger;
    actions: Action[];
    conditions?: Condition[];
}
export interface Trigger {
    type: 'onSubmit' | 'onLoad' | 'onSchedule' | 'onEvent';
    target: string;
    schedule?: string;
}
export interface Action {
    type: 'save' | 'update' | 'delete' | 'call' | 'navigate' | 'notify';
    target: string;
    params?: Record<string, any>;
}
export interface Condition {
    field: string;
    operator: 'eq' | 'ne' | 'gt' | 'gte' | 'lt' | 'lte' | 'in' | 'contains';
    value: any;
}
export interface Permission {
    id: string;
    resource: string;
    actions: PermissionAction[];
    roles: string[];
}
export type PermissionAction = 'create' | 'read' | 'update' | 'delete' | 'execute';
export interface Constraint {
    id: string;
    type: ConstraintType;
    rule: string;
    severity: 'error' | 'warning';
    validator?: string;
}
export type ConstraintType = 'RefIntegrity' | 'TypeConsistency' | 'PermissionValid' | 'FlowComplete' | 'UniqueConstraint' | 'Custom';
export interface TestCase {
    name: string;
    description?: string;
    steps: TestStep[];
    expect: TestExpectation;
}
export interface TestStep {
    action: 'render' | 'fill' | 'click' | 'submit' | 'wait' | 'navigate';
    target: string;
    value?: any;
    timeout?: number;
}
export interface TestExpectation {
    type: 'http' | 'dom' | 'data';
    condition: string;
}
export interface CustomComponent {
    name: string;
    framework: 'react' | 'vue' | 'angular';
    source: string;
    license: string;
    props?: ComponentProp[];
}
export interface ComponentProp {
    name: string;
    type: string;
    required: boolean;
    default?: any;
    description?: string;
}
export interface ThemeVariables {
    primary: string;
    secondary: string;
    accent?: string;
    neutral?: string;
    success?: string;
    warning?: string;
    error?: string;
    [key: string]: string | undefined;
}
export interface VersionInfo {
    id: string;
    appId: string;
    version: string;
    source: VersionSource;
    agentType?: AgentType;
    parentVersionId?: string;
    tenantId: string;
    createdBy?: string;
    createdAt: string;
    metadata?: VersionMetadata;
}
export type VersionSource = 'plan' | 'exec' | 'fix' | 'manual';
export type AgentType = 'plan' | 'execute' | 'validate';
export interface VersionMetadata {
    latencyMs?: number;
    modelProvider?: string;
    modelName?: string;
    tokenUsage?: TokenUsage;
    confidence?: number;
}
export interface TokenUsage {
    prompt: number;
    completion: number;
    total: number;
}
export interface ValidationResult {
    isValid: boolean;
    errors: ValidationError[];
    warnings: ValidationWarning[];
    suggestions: ValidationSuggestion[];
    score: number;
}
export interface ValidationError {
    type: string;
    severity: 'error';
    message: string;
    location: ValidationLocation;
    suggestion?: string;
}
export interface ValidationWarning {
    type: string;
    severity: 'warning';
    message: string;
    location: ValidationLocation;
    suggestion?: string;
}
export interface ValidationSuggestion {
    type: string;
    message: string;
    code?: string;
}
export interface ValidationLocation {
    path: string;
    line?: number;
    column?: number;
}
