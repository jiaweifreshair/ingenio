'use client';

import { AppComplexityMode, APP_MODES } from "@/types/smart-builder";
import { cn } from "@/lib/utils";
import { Check } from "lucide-react";

interface AppComplexitySelectorProps {
    selectedMode: AppComplexityMode | null;
    onSelect: (mode: AppComplexityMode) => void;
}

export function AppComplexitySelector({ selectedMode, onSelect }: AppComplexitySelectorProps) {
    return (
        <section className="py-12 px-6">
            <div className="max-w-6xl mx-auto">
                <div className="text-center mb-10">
                    <h2 className="text-2xl font-bold text-foreground mb-3">选择应用复杂度</h2>
                    <p className="text-muted-foreground">根据您的需求选择合适的复杂度，AI将智能匹配最佳技术方案</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    {APP_MODES.map((mode) => {
                        const isSelected = selectedMode === mode.id;
                        const Icon = mode.icon;

                        return (
                            <div
                                key={mode.id}
                                onClick={() => onSelect(mode.id)}
                                className={cn(
                                    "relative group cursor-pointer rounded-2xl p-6 border transition-all duration-300",
                                    "hover:shadow-lg hover:-translate-y-1",
                                    isSelected
                                        ? "border-primary ring-2 ring-primary/20 bg-primary/5 dark:bg-primary/10 shadow-md"
                                        : "border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/50"
                                )}
                            >
                                {/* Selection Indicator */}
                                {isSelected && (
                                    <div className="absolute top-4 right-4 bg-primary text-primary-foreground w-6 h-6 rounded-full flex items-center justify-center">
                                        <Check className="w-3.5 h-3.5" />
                                    </div>
                                )}

                                <div className={cn(
                                    "w-12 h-12 rounded-xl flex items-center justify-center mb-4 text-white shadow-sm",
                                    mode.colorClass
                                )}>
                                    <Icon className="w-6 h-6" />
                                </div>

                                <h3 className="text-lg font-bold text-foreground mb-1">{mode.title}</h3>
                                <p className="text-xs text-muted-foreground mb-4 min-h-[40px]">
                                    {mode.description}
                                </p>

                                <div className={cn(
                                    "inline-block px-3 py-1 rounded-full text-xs font-semibold mb-4",
                                    isSelected ? "bg-primary text-primary-foreground" : "bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400"
                                )}>
                                    {mode.techStack}
                                </div>

                                <div className="flex flex-wrap gap-2">
                                    {mode.tags.map(tag => (
                                        <span key={tag} className="px-2 py-0.5 rounded text-[10px] bg-slate-100 dark:bg-slate-800 text-slate-500 font-medium">
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </section>
    );
}
