'use client';

import { INDUSTRIES, IndustryType } from "@/types/smart-builder";
import { cn } from "@/lib/utils";
import { ArrowRight, Check } from "lucide-react";

interface IndustrySelectionProps {
    selectedIndustry: IndustryType | null;
    onSelect: (industry: IndustryType) => void;
}

export function IndustrySelection({ selectedIndustry, onSelect }: IndustrySelectionProps) {
    return (
        <section className="py-12 bg-slate-50 dark:bg-slate-900/20">
            <div className="container mx-auto px-6 max-w-6xl">
                <div className="text-center mb-12">
                    <h2 className="text-4xl font-bold text-foreground mb-4">全行业场景覆盖</h2>
                    <div className="flex items-center justify-center gap-2 text-muted-foreground text-lg">
                        <span>点击选择您的业务场景,</span>
                        <span className="text-emerald-500 font-semibold">AI将自动优化您的需求描述</span>
                    </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
                    {INDUSTRIES.map((industry) => {
                        const isSelected = selectedIndustry === industry.id;
                        const Icon = industry.icon;
                        const isMore = industry.id === 'MORE';

                        if (isMore) {
                            return (
                                <div
                                    key={industry.id}
                                    onClick={() => onSelect(industry.id)}
                                    className={cn(
                                        "group relative flex flex-col items-center justify-center p-8 rounded-[2rem] border-2 border-dashed border-slate-200 dark:border-slate-800 cursor-pointer transition-all duration-300",
                                        "hover:border-slate-300 dark:hover:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800/50",
                                        isSelected && "border-emerald-500 bg-emerald-50 dark:bg-emerald-900/10"
                                    )}
                                >
                                    {isSelected && (
                                        <div className="absolute top-4 right-4 bg-emerald-500 text-white w-5 h-5 rounded-full flex items-center justify-center">
                                            <Check className="w-3 h-3" />
                                        </div>
                                    )}
                                    <div className="w-14 h-14 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center mb-4 group-hover:bg-slate-200 dark:group-hover:bg-slate-700 transition-colors">
                                        <ArrowRight className="w-6 h-6 text-slate-500" />
                                    </div>
                                    <h3 className="text-lg font-medium text-slate-500 dark:text-slate-400">{industry.label}</h3>
                                </div>
                            );
                        }

                        return (
                            <div
                                key={industry.id}
                                onClick={() => onSelect(industry.id)}
                                className={cn(
                                    "group relative p-8 rounded-[2rem] border bg-white dark:bg-slate-900/50 cursor-pointer transition-all duration-300",
                                    "hover:shadow-lg hover:-translate-y-1",
                                    isSelected
                                        ? "border-emerald-500 ring-2 ring-emerald-500/20 shadow-md"
                                        : "border-slate-200 dark:border-slate-800"
                                )}
                            >
                                {isSelected && (
                                    <div className="absolute top-4 right-4 bg-emerald-500 text-white w-6 h-6 rounded-full flex items-center justify-center">
                                        <Check className="w-3.5 h-3.5" />
                                    </div>
                                )}

                                <div className="flex flex-col items-center text-center">
                                    <div className={cn(
                                        "w-14 h-14 rounded-2xl flex items-center justify-center mb-6 transition-colors",
                                        isSelected
                                            ? "bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600"
                                            : "bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 group-hover:bg-emerald-50 dark:group-hover:bg-emerald-900/10 group-hover:text-emerald-600"
                                    )}>
                                        <Icon className="w-7 h-7" />
                                    </div>

                                    <h3 className="text-xl font-bold text-foreground mb-2">{industry.label}</h3>
                                    <p className="text-sm text-muted-foreground">{industry.description}</p>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </section>
    );
}
