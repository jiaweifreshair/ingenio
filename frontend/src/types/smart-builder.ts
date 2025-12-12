import { Layout, Globe, Building2, Smartphone, ShoppingBag, GraduationCap, Users, Coffee, Briefcase, Landmark, Clapperboard, MonitorPlay, BrainCircuit, ScanText, Mic, Workflow, type LucideIcon } from "lucide-react";

// --- Types ---

export type AppComplexityMode = 'H5' | 'WEB' | 'ENTERPRISE' | 'NATIVE';

export interface AppModeConfig {
    id: AppComplexityMode;
    icon: LucideIcon; // Lucide icon
    title: string;
    description: string;
    techStack: string;
    tags: string[];
    colorClass: string; // Tailwnd class for icon bg
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
        id: 'H5',
        icon: Layout,
        title: "多端套壳应用",
        description: "内容展示、表单、列表类，无原生交互",
        techStack: "H5 + WebView",
        tags: ["待办清单", "新闻阅读", "商品展示", "问卷表单"],
        colorClass: "bg-emerald-500",
    },
    {
        id: 'WEB',
        icon: Globe,
        title: "纯Web应用",
        description: "仅浏览器运行，SaaS或Dashboard",
        techStack: "React + Supabase",
        tags: ["博客系统", "管理后台", "数据看板", "预约系统"],
        colorClass: "bg-blue-500",
    },
    {
        id: 'ENTERPRISE',
        icon: Building2,
        title: "企业级应用",
        description: "复杂业务逻辑，多实体关联系统",
        techStack: "React + Spring Boot",
        tags: ["电商平台", "企业ERP", "在线教育", "多租户SaaS"],
        colorClass: "bg-purple-500",
    },
    {
        id: 'NATIVE',
        icon: Smartphone,
        title: "原生跨端应用",
        description: "相机/GPS/蓝牙等原生能力",
        techStack: "Kuikly",
        tags: ["Android", "iOS", "HarmonyOS", "Web", "运动打卡", "扫码工具"],
        colorClass: "bg-orange-500",
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
