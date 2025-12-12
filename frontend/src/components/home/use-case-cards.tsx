"use client";

import { Card } from "@/components/ui/card";
import { useRouter } from "next/navigation";

/**
 * 使用案例接口
 */
interface UseCase {
  /** 标题 */
  title: string;
  /** 描述 */
  description: string;
  /** 示例ID */
  exampleId: string;
}

/**
 * 校园案例数据
 */
const useCases: ReadonlyArray<UseCase> = [
  {
    title: "报名签到",
    description: "一次创建，多场景复用；数据导出与去重更省心。",
    exampleId: "demo-signup",
  },
  {
    title: "问卷表单",
    description: "逻辑跳转、匿名收集；统计图一键生成。",
    exampleId: "demo-survey",
  },
  {
    title: "社团小店",
    description: "上架-下单-支付-对账一步到位。",
    exampleId: "demo-shop",
  },
] as const;

/**
 * UseCaseCards组件
 * 校园使用案例卡片展示
 */
export function UseCaseCards(): React.ReactElement {
  const router = useRouter();

  const handleCardClick = (exampleId: string) => {
    // 跳转到对应的预览页面
    router.push(`/preview/${exampleId}`);
  };

  return (
    <section id="usecases" className="w-full py-24">
      <div className="container mx-auto px-6">
        <div className="mx-auto flex max-w-[58rem] flex-col items-center space-y-4 text-center mb-16">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground tracking-tight">
            精选校园案例
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl">
            覆盖学习、生活、社团多种场景，满足你的多样化需求
          </p>
        </div>

        <div className="grid gap-8 md:grid-cols-3">
          {useCases.map((useCase) => (
            <Card
              key={useCase.title}
              className="group relative cursor-pointer bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-[2rem] shadow-sm hover:shadow-xl transition-all duration-300 hover:-translate-y-1 overflow-hidden"
              onClick={() => handleCardClick(useCase.exampleId)}
            >
              <div className="p-8 h-full flex flex-col">
                <div className="mb-4 w-12 h-12 rounded-2xl bg-blue-100/50 dark:bg-blue-900/30 flex items-center justify-center text-blue-600 dark:text-blue-400">
                  <div className="w-6 h-6 rounded-full border-2 border-current opacity-60" />
                </div>
                
                <h3 className="text-2xl font-bold text-foreground mb-3 group-hover:text-blue-600 transition-colors">
                  {useCase.title}
                </h3>
                
                <p className="text-muted-foreground leading-relaxed flex-1">
                  {useCase.description}
                </p>
                
                <div className="mt-6 flex items-center text-sm font-medium text-blue-600 dark:text-blue-400 opacity-0 -translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-300">
                  查看预览 <span className="ml-1">→</span>
                </div>
              </div>
            </Card>
          ))}
        </div>

        <div className="mt-12 text-center">
          <p className="text-sm text-muted-foreground font-medium">
            点击卡片即可直接体验应用原型
          </p>
        </div>
      </div>
    </section>
  );
}
