
import { 
  ShoppingBag, 
  GraduationCap, 
  Users, 
  Coffee, 
  Briefcase, 
  Landmark, 
  Clapperboard,
  type LucideIcon
} from "lucide-react";

export interface ScenarioConfig {
  id: string;
  title: string;
  description: string;
  color: string;
  icon: LucideIcon;
  prompt: string;
  keywords: string[];
  complexityHint?: string;
  techStackHint?: string;
}

export const SCENARIO_CONFIGS: Record<string, ScenarioConfig> = {
  ecommerce: {
    id: "ecommerce",
    title: "电商",
    description: "商城、团购、分销",
    color: "text-blue-500",
    icon: ShoppingBag,
    prompt: "创建一个全功能的B2C电商平台，包含商品展示、购物车、订单管理、支付集成和用户个人中心。",
    keywords: ["电商", "商城", "支付", "订单"],
    complexityHint: 'COMPLEX',
    techStackHint: 'React + Spring Boot'
  },
  education: {
    id: "education",
    title: "教育",
    description: "网课、题库、教务",
    color: "text-indigo-500",
    icon: GraduationCap,
    prompt: "构建一个在线教育系统，支持课程发布、视频点播、在线题库练习、学生考试以及教务管理功能。",
    keywords: ["教育", "课程", "考试", "学生"],
    complexityHint: 'COMPLEX',
    techStackHint: 'React + Spring Boot'
  },
  social: {
    id: "social",
    title: "社交",
    description: "社区、交友、论坛",
    color: "text-pink-500",
    icon: Users,
    prompt: "开发一个垂直领域的社交社区应用，包含用户动态发布、圈子讨论、即时通讯(IM)和关注粉丝机制。",
    keywords: ["社交", "社区", "动态", "好友"],
    complexityHint: 'MEDIUM',
    techStackHint: 'React+Supabase'
  },
  life: {
    id: "life",
    title: "生活",
    description: "外卖、跑腿、家政",
    color: "text-orange-500",
    icon: Coffee,
    prompt: "搭建一个本地生活服务平台，涵盖外卖点餐、跑腿代购、家政预约等O2O服务，支持LBS定位。",
    keywords: ["生活服务", "外卖", "预约", "O2O"],
    complexityHint: 'COMPLEX',
    techStackHint: 'React + Spring Boot'
  },
  enterprise: {
    id: "enterprise",
    title: "企管",
    description: "OA、CRM、ERP",
    color: "text-slate-500",
    icon: Briefcase,
    prompt: "设计一套企业级管理系统(SaaS)，集成OA办公自动化、CRM客户关系管理和ERP进销存模块。",
    keywords: ["企业管理", "OA", "CRM", "SaaS"],
    complexityHint: 'COMPLEX',
    techStackHint: 'React + Spring Boot'
  },
  finance: {
    id: "finance",
    title: "金融",
    description: "理财、记账、借贷",
    color: "text-yellow-500",
    icon: Landmark,
    prompt: "开发一个个人财务管理应用，具备收支记账、资产分析、理财规划和多账户同步功能。",
    keywords: ["金融", "记账", "理财", "资产"],
    complexityHint: 'MEDIUM',
    techStackHint: 'React+Supabase'
  },
  media: {
    id: "media",
    title: "媒体",
    description: "资讯、直播、短视频",
    color: "text-red-500",
    icon: Clapperboard,
    prompt: "构建一个多媒体内容分发平台，支持新闻资讯聚合、短视频流浏览、直播互动和内容创作者中心。",
    keywords: ["媒体", "视频", "直播", "资讯"],
    complexityHint: 'MEDIUM',
    techStackHint: 'React+Supabase'
  }
};

/**
 * 智能优化需求描述
 * 将新选择的场景 Prompt 融合到现有需求中
 */
export function optimizeRequirement(currentRequirement: string, scenarioId: string): string {
  const scenario = SCENARIO_CONFIGS[scenarioId];
  if (!scenario) return currentRequirement;

  const trimmed = currentRequirement.trim();
  
  // 1. 如果当前为空，直接返回场景 Prompt
  if (!trimmed) {
    return scenario.prompt;
  }

  // 2. 如果当前内容已经包含了该场景的关键词，可能不需要重复添加 (简单去重)
  // 这里简化处理：直接追加，让用户自己编辑，或者用更智能的连接词
  
  // 3. 智能连接
  // 简单的连接逻辑：如果结尾没有标点，加句号。然后加上 "同时集成[场景名]功能..."
  
  const lastChar = trimmed.slice(-1);
  const needsPunctuation = !['.', '。', '!', '！', '?', '？'].includes(lastChar);
  const connector = needsPunctuation ? "。" : "";
  
  // 生成融合后的 Prompt
  // 策略：保留用户原有输入，追加场景特性
  // 例如："我想做一个蓝色APP" + 电商 -> "我想做一个蓝色APP。此外，需包含电商平台的核心功能：商品展示、购物车、订单管理..."
  
  // 简化版 Prompt 提取 (去掉 "创建一个..." 这样的动词前缀，提取核心名词短语)
  // 这是一个简化的 NLP 处理
  let featureDescription = scenario.prompt;
  
  // 尝试提取 "包含..." 后面的内容
  const match = scenario.prompt.match(/包含(.*)/);
  if (match && match[1]) {
    featureDescription = `需包含${match[1]}`;
  }

  return `${trimmed}${connector} 同时${featureDescription}`;
}
