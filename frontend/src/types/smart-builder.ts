import { Globe, Building2, Smartphone, ShoppingBag, GraduationCap, Users, Coffee, Briefcase, Landmark, Clapperboard, MonitorPlay, BrainCircuit, ScanText, Mic, Workflow, type LucideIcon } from "lucide-react";

// --- Types ---

export type AppComplexityMode = 'WEB' | 'ENTERPRISE' | 'NATIVE';

export interface AppModeConfig {
    id: AppComplexityMode;
    icon: LucideIcon; // Lucide icon
    title: string;
    description: string;
    techStack: string;
    tags: string[];
    colorClass: string; // Tailwnd class for icon bg
    exampleTitle?: string;
    examplePrompt?: string;
    disabled?: boolean;
}

export type IndustryType = 'ECOMMERCE' | 'EDUCATION' | 'SOCIAL' | 'LIFE' | 'ENTERPRISE' | 'FINANCE' | 'MEDIA' | 'MORE';

export interface IndustryConfig {
    id: IndustryType;
    icon: LucideIcon;
    label: string;
    description: string;
    promptContext: string; // Added to prompt
}

export type AICapabilityType = 'VISION' | 'LANGUAGE' | 'VOICE' | 'LOGIC';

export interface AICapabilityConfig {
    id: AICapabilityType;
    icon: LucideIcon;
    label: string;
    description: string;
    promptDetail: string; // Detail added to prompt
}

// --- Data ---

export const APP_MODES: AppModeConfig[] = [
    {
        id: 'WEB',
        icon: Globe,
        title: "简单网页应用",
        description: "仅浏览器运行，SaaS或Dashboard",
        techStack: "React + Supabase",
        tags: ["博客系统", "管理后台", "数据看板", "预约系统"],
        colorClass: "bg-blue-500",
        exampleTitle: "情绪小帮手",
        examplePrompt: "设计一个“情绪小帮手”应用，帮助同学记录每天的心情并得到简单安慰。学生需要用自然语言描述一个常见的小烦恼（如考试前紧张、和同学闹别扭），并把它转化成1-3个功能：如“选择今天的心情表情”“输入小烦恼”“得到一句鼓励或小建议”。作品需有清晰的使用入口和结束页面，能在3分钟内教会同学使用，并附一段简短说明文字：这个应用是为谁解决什么问题。"
    },
    {
        id: 'ENTERPRISE',
        icon: Building2,
        title: "复杂网页应用",
        description: "复杂业务逻辑，多实体关联系统",
        techStack: "React + Spring Boot",
        tags: ["电商平台", "企业ERP", "在线教育", "多租户SaaS"],
        colorClass: "bg-purple-500",
        exampleTitle: "青少年压力管理智能系统",
        examplePrompt: "设计一个“青少年压力管理智能系统”，为学生、班主任和心理老师提供一体化支持。需求包括：① 学生端：压力自评、情绪记录、个性化练习推荐；② 老师/管理端：班级整体压力数据看板与预警列表；③ 至少2个协作Agent（如“情绪评估Agent”“建议生成Agent”“风险预警Agent”）及其分工说明；④ 数据流程与隐私方案说明（哪些数据保存、如何匿名展示）。需提交系统架构图和1-2个典型使用场景故事。"
    },
    {
        id: 'NATIVE',
        icon: Smartphone,
        title: "原生跨端应用",
        description: "相机/GPS/蓝牙等原生能力",
        techStack: "Kuikly + Spring Boot",
        tags: ["Android", "iOS", "HarmonyOS", "Web", "运动打卡", "扫码工具"],
        colorClass: "bg-orange-500",
        exampleTitle: "校园情绪加油站",
        examplePrompt: "设计一个“校园情绪加油站”系统，围绕“学习压力过大”这一问题进行功能拆解。需求至少包含：① 信息收集模块（如压力自评问卷或情景选择）；② 分析模块（根据回答给出压力等级和可能原因）；③ 建议模块（给出不同等级的应对建议或练习）；④ 记录模块（简单的历史记录或打卡）。需提交一份流程图，说明用户从进入系统到获得建议的完整路径，并在文案中标注哪些环节使用了哪条心理学原理。",
        disabled: true
    }
];

export const INDUSTRIES: IndustryConfig[] = [
    { id: 'ECOMMERCE', icon: ShoppingBag, label: '电商', description: '商城、团购、分销', promptContext: 'an E-commerce platform' },
    { id: 'EDUCATION', icon: GraduationCap, label: '教育', description: '网课、题库、教务', promptContext: 'an Educational platform' },
    { id: 'SOCIAL', icon: Users, label: '社交', description: '社区、交友、论坛', promptContext: 'a Social Networking app' },
    { id: 'LIFE', icon: Coffee, label: '生活', description: '外卖、跑腿、家政', promptContext: 'a Lifestyle service app' },
    { id: 'ENTERPRISE', icon: Briefcase, label: '企管', description: 'OA、CRM、ERP', promptContext: 'an Enterprise Management system' },
    { id: 'FINANCE', icon: Landmark, label: '金融', description: '理财、记账、借贷', promptContext: 'a Financial service app' },
    { id: 'MEDIA', icon: Clapperboard, label: '媒体', description: '资讯、直播、短视频', promptContext: 'a Media streaming app' },
    { id: 'MORE', icon: MonitorPlay, label: '更多场景', description: '', promptContext: 'a custom application' },
];

export const AI_CAPABILITIES: AICapabilityConfig[] = [
    {
        id: 'VISION',
        icon: ScanText,
        label: '视觉识别 (Vision)',
        description: 'OCR文字提取、物体检测',
        promptDetail: 'integrate Computer Vision for OCR and object detection'
    },
    {
        id: 'LANGUAGE',
        icon: BrainCircuit,
        label: '大语言模型 (LLM)',
        description: '智能润色、摘要、RAG问答',
        promptDetail: 'use LLM for text summarization, refinement, and RAG'
    },
    {
        id: 'VOICE',
        icon: Mic,
        label: '语音交互 (Voice)',
        description: '语音转文字(ASR)、合成(TTS)',
        promptDetail: 'enable Voice interaction with ASR and TTS'
    },
    {
        id: 'LOGIC',
        icon: Workflow,
        label: '智能逻辑 (Logic)',
        description: '意图路由、自动化工作流',
        promptDetail: 'implement intelligent routing and automated workflows'
    },
];
