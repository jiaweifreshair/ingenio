"use client";

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { useLanguage } from "@/contexts/LanguageContext";

/**
 * FAQ条目接口
 */
interface FAQItem {
  /** 问题 */
  question: string;
  /** 答案 */
  answer: string;
}

// Removed static faqs array

/**
 * FAQAccordion组件
 * 常见问题手风琴展示
 */
export function FAQAccordion(): React.ReactElement {
  const { t } = useLanguage();

  const faqs: ReadonlyArray<FAQItem> = [
    {
      question: t('faq.q1'),
      answer: t('faq.a1'),
    },
    {
      question: t('faq.q2'),
      answer: t('faq.a2'),
    },
    {
      question: t('faq.q3'),
      answer: t('faq.a3'),
    },
  ] as const;

  return (
    <section id="faq" className="w-full py-12">
      {/* FAQ 结构化数据：显著提升国内搜索引擎和 AI 提取准确率 */}
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "FAQPage",
            "mainEntity": faqs.map(faq => ({
              "@type": "Question",
              "name": faq.question,
              "acceptedAnswer": {
                "@type": "Answer",
                "text": faq.answer
              }
            }))
          })
        }}
      />
      <div className="container mx-auto px-6">
        <div className="mx-auto flex max-w-[58rem] flex-col items-center space-y-4 text-center mb-10">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground tracking-tight">
            {t('faq.title')}
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl">
            {t('faq.subtitle')}
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
