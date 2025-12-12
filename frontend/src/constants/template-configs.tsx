/**
 * 模板配置常量
 * 首页快捷入口的6个模板卡片配置
 *
 * @author Ingenio Team
 * @since V2.0
 */

import {
  ShoppingCart,
  Utensils,
  FileText,
  GraduationCap,
  Heart,
  Calendar,
} from 'lucide-react';
import type { TemplateCardProps } from '@/components/home/template-card';

/**
 * 首页快捷入口的6个模板
 * 与requirement-form保持一致
 */
export const TEMPLATE_CONFIGS: TemplateCardProps[] = [
  {
    id: 'campus-marketplace',
    title: '校园二手交易',
    description: '闲置物品交易平台',
    icon: <ShoppingCart className="h-6 w-6" />,
    color: 'from-green-500 to-emerald-500',
  },
  {
    id: 'calorie-tracker',
    title: '校食热量查询',
    description: '食堂营养健康助手',
    icon: <Utensils className="h-6 w-6" />,
    color: 'from-orange-500 to-amber-500',
  },
  {
    id: 'vocabulary-trainer',
    title: '单词刷题宝',
    description: '英语学习打卡',
    icon: <FileText className="h-6 w-6" />,
    color: 'from-blue-500 to-cyan-500',
  },
  {
    id: 'course-review',
    title: '课程评价助手',
    description: '选课必备神器',
    icon: <GraduationCap className="h-6 w-6" />,
    color: 'from-purple-500 to-pink-500',
  },
  {
    id: 'health-tracker',
    title: '健康打卡',
    description: '运动健身记录',
    icon: <Heart className="h-6 w-6" />,
    color: 'from-red-500 to-rose-500',
  },
  {
    id: 'study-scheduler',
    title: '学习日程表',
    description: '课程时间管理',
    icon: <Calendar className="h-6 w-6" />,
    color: 'from-indigo-500 to-violet-500',
  },
];
