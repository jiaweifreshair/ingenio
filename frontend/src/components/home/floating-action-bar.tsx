"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Sparkles, Maximize2, Minimize2, ArrowUp, Wand2, Link as LinkIcon, ChevronDown, Check, Zap, X } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  type IndustryType,
  type AppComplexityMode,
  type AICapabilityType,
  INDUSTRIES,
  APP_MODES,
  AI_CAPABILITIES
} from "@/types/smart-builder";
import { useLanguage } from "@/contexts/LanguageContext";

interface FloatingActionBarProps {
  requirement: string;
  onRequirementChange: (val: string) => void;
  onLaunchWizard: (prompt: string, context: { industry: IndustryType | null, mode: AppComplexityMode | null, capabilities: AICapabilityType[] }) => void;
  selectedIndustry?: IndustryType | null;
  selectedMode?: AppComplexityMode | null;
  selectedCapabilities?: AICapabilityType[];
  onIndustryChange?: (industry: IndustryType | null) => void;
  onModeChange?: (mode: AppComplexityMode | null) => void;
  onCapabilitiesChange?: (capabilities: AICapabilityType[]) => void;
}

type GenerationMode = 'NEW_IDEA' | 'REDESIGN_SITE';

export function FloatingActionBar({
  requirement,
  onRequirementChange,
  onLaunchWizard,
  selectedIndustry,
  selectedMode,
  selectedCapabilities,
  onIndustryChange,
  onModeChange,
  onCapabilitiesChange
}: FloatingActionBarProps) {
  const { t } = useLanguage();
  const [isVisible, setIsVisible] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [generationMode, setGenerationMode] = useState<GenerationMode>('NEW_IDEA');
  const [url, setUrl] = useState("");
  const [localCapabilities, setLocalCapabilities] = useState<AICapabilityType[]>(selectedCapabilities || []);

  // Sync props to local state
  useEffect(() => {
    if (selectedCapabilities !== undefined) setLocalCapabilities(selectedCapabilities);
  }, [selectedCapabilities]);

  // Scroll detection
  useEffect(() => {
    const handleScroll = () => {
      // Show when scrolled past Hero (approx 500px)
      if (window.scrollY > 500) {
        setIsVisible(true);
      } else {
        setIsVisible(false);
        setIsExpanded(false); // Auto collapse when hiding
      }
    };

    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const getFullPrompt = () => {
    if (generationMode === 'REDESIGN_SITE') {
        const userReq = requirement.trim() ? `\nUser Instructions: ${requirement}` : '';
        return `Redesign/Refactor the website at this URL: ${url}.${userReq}`;
    }

    const parts = [];

    // 1. Tags Context
    if (selectedIndustry) {
      const industry = INDUSTRIES.find(i => i.id === selectedIndustry);
      if (industry) parts.push(`Industry: ${industry.label} (${industry.promptContext}).`);
    }
    if (selectedMode) {
      const mode = APP_MODES.find(m => m.id === selectedMode);
      if (mode) parts.push(`App Mode: ${mode.title} (${mode.techStack}).`);
    }
    if (selectedCapabilities && selectedCapabilities.length > 0) {
      const caps = selectedCapabilities.map(id => AI_CAPABILITIES.find(c => c.id === id)?.label).join(", ");
      const capsDetail = selectedCapabilities.map(id => AI_CAPABILITIES.find(c => c.id === id)?.promptDetail).join("; ");
      parts.push(`AI Capabilities: ${caps}. Features: ${capsDetail}.`);
    }

    // 2. User Text
    if (requirement.trim()) {
      parts.push(`Requirement Description: ${requirement}`);
    } else {
      if (parts.length > 0) parts.push("Please build this application.");
    }

    return parts.join("\n");
  };

  const handleGenerate = () => {
    if (generationMode === 'REDESIGN_SITE' && !url.trim()) {
        // Simple alert or toast could be better, but for now just don't submit if URL missing in redesign mode
        return;
    }
    const fullPrompt = getFullPrompt();
    if (!fullPrompt.trim() && !requirement.trim()) return;
    
    onLaunchWizard(fullPrompt, {
        industry: selectedIndustry || null,
        mode: selectedMode || null,
        capabilities: selectedCapabilities || []
    });
  };

  const toggleExpand = () => {
    setIsExpanded(!isExpanded);
  };

  // 删除标签处理函数
  const handleRemoveIndustry = () => {
    onIndustryChange?.(null);
  };

  const handleRemoveMode = () => {
    onModeChange?.(null);
  };

  if (!isVisible) return null;

  return (
    <div
      className={cn(
        "fixed top-0 left-0 right-0 z-[100] flex justify-center pt-4 px-4 transition-all duration-300 transform pointer-events-none",
        isVisible ? "translate-y-0 opacity-100" : "-translate-y-full opacity-0"
      )}
    >
      <div
        className={cn(
          "w-full bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border border-slate-200 dark:border-slate-700 shadow-xl rounded-2xl transition-all duration-300 overflow-hidden pointer-events-auto",
          isExpanded ? "max-w-4xl" : "max-w-3xl"
        )}
      >
        {/* Tags Row (if any tags selected) */}
        {generationMode === 'NEW_IDEA' && (selectedMode || selectedIndustry || localCapabilities.length > 0) && (
          <div className="flex flex-wrap gap-2 items-center px-4 pt-3 pb-2">
            {/* Mode Tag */}
            {selectedMode && (() => {
              const mode = APP_MODES.find(m => m.id === selectedMode);
              if (!mode) return null;
              const Icon = mode.icon;
              return (
                <div key={mode.id} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 text-sm">
                  <Icon className="w-4 h-4" />
                  <span>{t(`const.mode.${mode.id}.title`)}</span>
                  <button
                    onClick={handleRemoveMode}
                    className="ml-1 hover:bg-blue-200 rounded-full p-0.5 transition-colors"
                    aria-label="移除"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              );
            })()}

            {/* Industry Tag */}
            {selectedIndustry && (() => {
              const industry = INDUSTRIES.find(i => i.id === selectedIndustry);
              if (!industry) return null;
              const Icon = industry.icon;
              return (
                <div key={industry.id} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-green-50 text-green-700 border border-green-200 text-sm">
                  <Icon className="w-4 h-4" />
                  <span>{t(`const.industry.${industry.id}.label`)}</span>
                  <button
                    onClick={handleRemoveIndustry}
                    className="ml-1 hover:bg-green-200 rounded-full p-0.5 transition-colors"
                    aria-label="移除"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              );
            })()}

            {/* AI Capabilities Tag */}
            {localCapabilities.length > 0 && (
              <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-purple-50 text-purple-700 border border-purple-200 text-sm">
                <Zap className="w-4 h-4 fill-purple-500" />
                <span>{t('hero.selector_ai')} · {t('common.more').replace('More', '')}{localCapabilities.length}</span>
                <button
                  onClick={() => {
                    setLocalCapabilities([]);
                    onCapabilitiesChange?.([]);
                  }}
                  className="ml-1 hover:bg-purple-200 rounded-full p-0.5 transition-colors"
                  aria-label="移除"
                >
                  <X className="w-3 h-3" />
                </button>
              </div>
            )}
          </div>
        )}

        {/* Main Input Row */}
        <div className="flex items-start gap-2 p-2">
            {/* Mode Switcher */}
            <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="h-9 gap-1 px-2 text-muted-foreground hover:text-foreground shrink-0">
                {generationMode === 'NEW_IDEA' ? (
                    <>
                    <Sparkles className="w-4 h-4 text-amber-500" />
                    <span className="hidden sm:inline">{t('hero.mode_create')}</span>
                    </>
                ) : (
                    <>
                    <Wand2 className="w-4 h-4 text-purple-500" />
                    <span className="hidden sm:inline">{t('hero.mode_redesign')}</span>
                    </>
                )}
                <ChevronDown className="w-3 h-3 opacity-50" />
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="z-[110]">
                <DropdownMenuItem onClick={() => setGenerationMode('NEW_IDEA')}>
                <Sparkles className="w-4 h-4 mr-2 text-amber-500" />
                {t('hero.mode_create')}
                {generationMode === 'NEW_IDEA' && <Check className="w-3 h-3 ml-auto" />}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => setGenerationMode('REDESIGN_SITE')}>
                <Wand2 className="w-4 h-4 mr-2 text-purple-500" />
                {t('hero.mode_redesign')}
                {generationMode === 'REDESIGN_SITE' && <Check className="w-3 h-3 ml-auto" />}
                </DropdownMenuItem>
            </DropdownMenuContent>
            </DropdownMenu>

           {/* Input Area */}
           <div className="flex-1 flex flex-col gap-2 relative">
            {generationMode === 'REDESIGN_SITE' && (
                <div className="relative w-full animate-in fade-in slide-in-from-top-1 duration-200">
                    <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
                        <LinkIcon className="w-3.5 h-3.5" />
                    </div>
                    <input
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        placeholder={t('hero.placeholder_redesign')}
                        className="w-full pl-8 pr-3 py-1.5 h-9 rounded-md border border-slate-200 dark:border-slate-700 bg-white/50 dark:bg-slate-900/50 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50"
                    />
                </div>
            )}
            <Textarea
                value={requirement}
                onChange={(e) => onRequirementChange(e.target.value)}
                placeholder={generationMode === 'REDESIGN_SITE' ? t('hero.placeholder_redesign') : t('hero.placeholder_create')}
                className={cn(
                    "w-full resize-none border-0 bg-transparent focus-visible:ring-0 px-3 py-2 text-sm",
                    isExpanded ? "min-h-[200px]" : "h-10 min-h-[40px] py-2" // Compact height matches button roughly
                )}
                style={{
                     // Hide scrollbar in compact mode if possible, but keep it functional
                     overflow: isExpanded ? 'auto' : 'hidden' 
                }}
            />
           </div>

           {/* Actions Group */}
           <div className="flex items-center gap-2 shrink-0 pr-1 self-start pt-1">
                {/* Expand Toggle */}
                <Button
                    variant="ghost"
                    size="icon"
                    onClick={toggleExpand}
                    className="h-8 w-8 text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                    title={isExpanded ? "收起" : "展开"}
                >
                    {isExpanded ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
                </Button>

                {/* Generate Button */}
                <Button
                    onClick={handleGenerate}
                    className="bg-[#0f172a] hover:bg-[#1e293b] text-white dark:bg-white dark:text-black dark:hover:bg-slate-200 h-9 px-4 text-sm font-medium rounded-xl shadow-sm transition-all hover:scale-105"
                >
                    <Sparkles className="w-3.5 h-3.5 mr-2" />
                    {t('hero.btn_generate')}
                </Button>
           </div>
        </div>
        
        {/* Expanded Content (Optional: Add more controls here if needed) */}
        {isExpanded && (
             <div className="px-4 pb-3 pt-0 text-xs text-slate-400 flex justify-between items-center border-t border-slate-100 dark:border-slate-800/50 mt-2">
                 <span>{t('floating.input_chars')} {requirement.length}</span>
                 <span className="flex items-center gap-1">
                     <ArrowUp className="w-3 h-3" />
                     {t('floating.scroll_top')}
                 </span>
             </div>
        )}
      </div>
    </div>
  );
}
