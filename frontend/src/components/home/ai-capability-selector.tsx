'use client';

import { AI_CAPABILITIES, AICapabilityType } from "@/types/smart-builder";
import { cn } from "@/lib/utils";
import { ArrowRight, Check, Plus } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";

interface AICapabilitySelectorProps {
    selectedCapabilities: AICapabilityType[];
    onToggle: (capability: AICapabilityType) => void;
}

export function AICapabilitySelector({ selectedCapabilities, onToggle }: AICapabilitySelectorProps) {
    const { t } = useLanguage();

    return (
        <section className="py-12 px-6">
            <div className="max-w-6xl mx-auto">
                <div className="text-center mb-10">
                    <h2 className="text-2xl font-bold text-foreground mb-3">{t('features.card_ai_title')}</h2>
                    <p className="text-muted-foreground">{t('features.card_ai_desc')}</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {/* Left: Large Banner/Interactive Area (Visual Representation) */}
                    <div className="hidden md:flex flex-col justify-center items-center bg-purple-50 dark:bg-purple-900/10 rounded-[2rem] p-10 border border-purple-100 dark:border-purple-900/20">
                        <div className="relative w-full max-w-sm aspect-square">
                            {/* Visual representation of 'AI Trinity' or similar could go here. For now, a placeholder illustration */}
                            <div className="absolute inset-0 flex items-center justify-center">
                                <div className="w-40 h-40 bg-purple-200 dark:bg-purple-800/40 rounded-full blur-3xl animate-pulse" />
                                <div className="relative z-10 text-center">
                                    <h3 className="text-3xl font-bold text-purple-600 dark:text-purple-300 mb-2">AI Native</h3>
                                    <p className="text-purple-500/80">{t('features.ai_empower')}</p>
                                </div>
                            </div>

                            {/* Floating icons related to selected capabilities could be cool here */}
                        </div>
                    </div>

                    {/* Right: Selection Grid */}
                    <div className="grid grid-cols-1 gap-4">
                        {AI_CAPABILITIES.map((cap) => {
                            const isSelected = selectedCapabilities.includes(cap.id);
                            const Icon = cap.icon;

                            return (
                                <div
                                    key={cap.id}
                                    onClick={() => onToggle(cap.id)}
                                    className={cn(
                                        "group flex items-center gap-4 p-5 rounded-2xl border cursor-pointer transition-all duration-200",
                                        "hover:shadow-md",
                                        isSelected
                                            ? "border-purple-500 bg-purple-50 dark:bg-purple-900/10"
                                            : "border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/50"
                                    )}
                                >
                                    <div className={cn(
                                        "w-12 h-12 rounded-xl flex items-center justify-center shrink-0 transition-colors",
                                        isSelected
                                            ? "bg-purple-100 dark:bg-purple-900/30 text-purple-600"
                                            : "bg-slate-100 dark:bg-slate-800 text-slate-500 group-hover:bg-purple-50 group-hover:text-purple-500"
                                    )}>
                                        <Icon className="w-6 h-6" />
                                    </div>

                                    <div className="flex-1">
                                        <h3 className="font-bold text-foreground">{t(`const.ai.${cap.id}.label`)}</h3>
                                        <p className="text-sm text-muted-foreground">{t(`const.ai.${cap.id}.desc`)}</p>
                                    </div>

                                    <div className={cn(
                                        "w-8 h-8 rounded-full flex items-center justify-center transition-all",
                                        isSelected
                                            ? "bg-purple-500 text-white"
                                            : "bg-slate-100 dark:bg-slate-800 text-slate-400 group-hover:bg-purple-100 group-hover:text-purple-600"
                                    )}>
                                        {isSelected ? <Check className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
                                    </div>
                                </div>
                            );
                        })}

                        <div className="mt-4 flex justify-end">
                            <button className="flex items-center text-purple-600 hover:text-purple-700 font-medium text-sm transition-colors">
                                {t('features.card_ai_btn')} <ArrowRight className="w-4 h-4 ml-1" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
