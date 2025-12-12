import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";

/**
 * FAQ条目接口
 */
interface FAQItem {
  /** 问题 */
  question: string;
  /** 答案 */
  answer: string;
}

/**
 * FAQ数据
 */
const faqs: ReadonlyArray<FAQItem> = [
  {
    question: "需要写代码吗？",
    answer:
      "不需要。秒构AI 提供可视化的向导式操作，完全不需要编程基础。对于高级用户，我们也提供专家模式进行深度定制。",
  },
  {
    question: "是否收费？",
    answer:
      "核心能力基础免费使用。我们提供订阅服务，包含更多高级功能、更大的存储空间，以及官方课程的学习权益。",
  },
  {
    question: "数据安全如何保障？",
    answer:
      "我们承诺不将用户的业务数据用于模型训练。所有数据采用加密存储和传输，支持随时导出和删除。我们符合相关数据保护法规的要求。",
  },
] as const;

/**
 * FAQAccordion组件
 * 常见问题手风琴展示
 */
export function FAQAccordion(): React.ReactElement {
  return (
    <section id="faq" className="w-full py-24">
      <div className="container mx-auto px-6">
        <div className="mx-auto flex max-w-[58rem] flex-col items-center space-y-4 text-center mb-16">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground tracking-tight">
            常见问题
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl">
            快速了解秒构 AI 的核心特性
          </p>
        </div>

        <div className="mx-auto mt-12 max-w-3xl">
          <Accordion type="single" collapsible className="w-full space-y-4">
            {faqs.map((faq, index) => (
              <AccordionItem 
                key={index} 
                value={`item-${index}`}
                className="border border-slate-200 dark:border-slate-800 rounded-2xl px-6 bg-slate-50/50 dark:bg-slate-900/50"
              >
                <AccordionTrigger className="text-left text-lg font-medium py-6 hover:no-underline hover:text-blue-600 transition-colors">
                  {faq.question}
                </AccordionTrigger>
                <AccordionContent className="text-muted-foreground text-base leading-relaxed pb-6">
                  {faq.answer}
                </AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </div>
      </div>
    </section>
  );
}
