/**
 * UI文本常量库
 * 统一管理所有页面和组件的文本内容，确保E2E测试和实际实现的一致性
 */

/**
 * 页面文本常量
 */
export const UI_TEXT = {
  /**
   * 首页文本
   */
  home: {
    title: '人人可用的应用生成器',
    subtitle: '为校园而生，用"选择 + 填空"在 30 分钟做出可发布的应用',
    primaryCta: '免费开始',
    secondaryCta: '观看 1 分钟示例',
  },

  /**
   * 创建页面文本
   */
  create: {
    title: '创建新应用',
    subtitle: '告诉我们你想要创建什么，AI会帮你自动生成应用架构',
    placeholder: '描述你想要的应用，例如：学生考勤系统、活动报名表单...',
    submitButton: '生成应用',
    submitButtonLoading: 'AI分析中...',
    cancelButton: '取消',
    templatesTitle: '快速模板',
    validationError: {
      tooShort: '请输入应用需求描述',
      minLength: '需求描述至少需要10个字符',
    },
  },

  /**
   * 向导页面文本
   */
  wizard: {
    title: 'AppSpec 生成向导',
    subtitle: '基于Dyad多Agent架构，正在为您生成智能应用规范',
    progressTitle: '总体进度',
    completedTitle: '生成完成！',
    steps: {
      plan: {
        name: '需求分析',
        description: 'PlanAgent正在分析用户需求，制定系统架构',
      },
      execute: {
        name: '应用生成',
        description: 'ExecuteAgent正在生成AppSpec应用规范',
      },
      validate: {
        name: '质量校验',
        description: 'ValidateAgent正在校验生成结果质量',
      },
    },
    actions: {
      createNew: '生成新的AppSpec',
      preview: '预览应用',
      publish: '发布应用',
      exportCode: '导出代码',
    },
  },

  /**
   * 预览页面文本
   */
  preview: {
    title: '应用预览',
    subtitle: '实时预览生成的应用',
    devices: {
      mobile: '手机',
      tablet: '平板',
      desktop: '桌面',
    },
    actions: {
      backToEdit: '返回编辑',
      publish: '发布应用',
    },
    sections: {
      appInfo: '应用信息',
      pages: '页面',
      dataModels: '数据模型',
    },
  },

  /**
   * 发布页面文本
   */
  publish: {
    title: '发布应用',
    subtitle: '选择发布平台并配置应用信息',
    platforms: {
      web: 'Web应用',
      mobile: '移动应用',
      desktop: '桌面应用',
    },
    form: {
      appName: '应用名称',
      appNamePlaceholder: '输入应用名称',
      appDescription: '应用描述',
      appDescriptionPlaceholder: '描述应用的功能和用途',
      domain: '自定义域名（可选）',
      domainPlaceholder: '留空将使用默认域名',
    },
    actions: {
      startPublish: '开始发布',
      publishing: {
        building: '构建中...',
        deploying: '部署中...',
      },
      copyLink: '复制链接',
      visitApp: '访问应用',
      preview: '预览',
    },
    status: {
      building: '正在构建应用...',
      buildingDesc: '正在编译代码和优化资源',
      deploying: '正在部署到服务器...',
      deployingDesc: '正在将应用部署到云端',
      published: '发布成功！',
      publishedDesc: '应用已成功发布到：',
      failed: '发布失败',
    },
  },

  /**
   * 通用文本
   */
  common: {
    loading: '加载中...',
    error: '出错了',
    success: '成功',
    cancel: '取消',
    confirm: '确认',
    save: '保存',
    delete: '删除',
    edit: '编辑',
    back: '返回',
  },
} as const;

/**
 * 快速模板数据
 */
export const QUICK_TEMPLATES = [
  {
    id: 'signup',
    name: '报名签到',
    description: '一次创建，多场景复用；数据导出与去重更省心。',
    requirement: '创建一个校园活动报名系统，支持活动发布、在线报名、签到和数据统计',
    icon: '📝',
  },
  {
    id: 'survey',
    name: '问卷表单',
    description: '逻辑跳转、匿名收集；统计图一键生成。',
    requirement: '创建一个问卷调查系统，支持多种题型、逻辑跳转和数据可视化',
    icon: '📊',
  },
  {
    id: 'shop',
    name: '社团小店',
    description: '上架-下单-支付-对账一步到位。',
    requirement: '创建一个社团商城系统，支持商品管理、订单处理、在线支付和对账',
    icon: '🛒',
  },
] as const;

/**
 * 类型导出
 */
export type TemplateId = (typeof QUICK_TEMPLATES)[number]['id'];
export type Template = (typeof QUICK_TEMPLATES)[number];
