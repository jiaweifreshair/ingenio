/**
 * 步骤接口
 */
interface Step {
  /** 步骤编号 */
  number: number;
  /** 标题 */
  title: string;
  /** 描述 */
  description: string;
}

/**
 * 三步法数据
 */
const steps: ReadonlyArray<Step> = [
  {
    number: 1,
    title: "描述需求",
    description: "用一句话告诉系统你要做什么。",
  },
  {
    number: 2,
    title: "智能生成",
    description: "自动拆解为步骤与草案，随改随看。",
  },
  {
    number: 3,
    title: "一键发布",
    description: "发布到网页/小程序，立刻投入使用。",
  },
] as const;

/**
 * StepsShowcase组件
 * 三步创建流程展示
 */
export function StepsShowcase(): React.ReactElement {
  return (
    <section id="how" className="w-full py-24 bg-slate-50/50 dark:bg-slate-950/50">
      <div className="container mx-auto px-6">
        <div className="mx-auto flex max-w-[58rem] flex-col items-center space-y-4 text-center mb-16">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground tracking-tight">
            简单三步，即刻上线
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl">
            摒弃繁琐的配置，专注于你的核心创意
          </p>
        </div>

        <div className="grid gap-8 md:grid-cols-3">
          {steps.map((step) => (
            <div
              key={step.number}
              className="group relative bg-white dark:bg-slate-900 rounded-[2.5rem] p-10 border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-xl transition-all duration-300 hover:-translate-y-1"
            >
              {/* 步骤编号 (背景水印) */}
              <div className="absolute top-4 right-6 text-8xl font-bold text-slate-100 dark:text-slate-800/50 select-none pointer-events-none group-hover:text-slate-200 dark:group-hover:text-slate-800 transition-colors">
                {step.number}
              </div>

              <div className="relative z-10 flex flex-col h-full">
                <div className="w-14 h-14 rounded-2xl bg-foreground text-background flex items-center justify-center text-xl font-bold mb-6 shadow-lg">
                  {step.number}
                </div>
                
                <h3 className="text-2xl font-bold text-foreground mb-4">
                  {step.title}
                </h3>
                
                <p className="text-muted-foreground text-lg leading-relaxed">
                  {step.description}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
