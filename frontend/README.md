# 秒构AI 前端

> 基于Next.js 15 + React 19 + TypeScript的智能应用生成平台前端

## 技术栈

- **框架**: Next.js 15 (App Router)
- **UI库**: React 19
- **样式**: TailwindCSS 3 + Shadcn/ui
- **状态管理**: Zustand
- **类型系统**: TypeScript 5.3+ (strict mode)
- **包管理**: pnpm
- **测试**: Vitest + Playwright
- **代码质量**: ESLint + Prettier

## 快速开始

### 安装依赖

```bash
pnpm install
```

### 启动开发服务器

```bash
pnpm dev
```

访问 [http://localhost:3000](http://localhost:3000) 查看应用。

### 构建生产版本

```bash
pnpm build
pnpm start
```

## 开发规范

### 代码质量检查

```bash
# TypeScript类型检查（必须0 errors）
pnpm run typecheck

# ESLint检查
pnpm run lint

# 代码格式化
pnpm run format
```

### 测试

```bash
# 单元测试
pnpm test

# 测试覆盖率（目标≥85%）
pnpm run test:coverage

# E2E测试
pnpm run e2e

# E2E测试UI模式
pnpm run e2e:ui
```

## 项目结构

```
frontend/
├── src/
│   ├── app/                    # Next.js App Router
│   │   ├── layout.tsx         # 根布局
│   │   └── page.tsx           # 首页
│   ├── components/             # React组件
│   │   └── ui/                # Shadcn/ui基础组件
│   ├── lib/                    # 工具函数
│   ├── styles/                 # 全局样式
│   ├── types/                  # TypeScript类型定义
│   ├── test/                   # 测试配置
│   └── e2e/                    # E2E测试
├── public/                     # 静态资源
└── [配置文件]
```

## 主题配色

- **校园绿**: #2bb673 (主色)
- **湖蓝**: #2c8ae8 (辅色)
- **强调色**: #ffc857

## 环境变量

复制 `.env.example` 为 `.env.local` 并配置：

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

## 代码提交规范

遵循约定式提交（Conventional Commits）：

- `feat:` 新增功能
- `fix:` 修复问题
- `refactor:` 重构代码
- `docs:` 文档更新
- `style:` 代码格式
- `test:` 测试相关
- `chore:` 构建/工具变更

## 质量标准

### 提交前检查清单

- [ ] TypeScript检查通过 (`pnpm run typecheck`)
- [ ] ESLint检查通过 (`pnpm run lint`)
- [ ] 单元测试通过 (`pnpm test`)
- [ ] 测试覆盖率≥85% (`pnpm run test:coverage`)
- [ ] 代码已格式化 (`pnpm run format`)

### 性能指标

- 首屏关键模块 LCP ≤ 2.5s（桌面）
- 预览可用 ≤ 10s
- Lighthouse分数 ≥ 90

## 常见问题

### pnpm安装失败

```bash
# 清理缓存重试
pnpm store prune
pnpm install
```

### TypeScript错误

```bash
# 清理Next.js缓存
rm -rf .next
pnpm run typecheck
```

### 端口占用

修改 `package.json` 中的dev script：

```json
"dev": "next dev -p 3002"
```

## License

MIT
