"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Sparkles, Maximize2, Minimize2, ArrowUp, Wand2, Link as LinkIcon, ChevronDown, Check } from "lucide-react";
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

interface FloatingActionBarProps {
  requirement: string;
  onRequirementChange: (val: string) => void;
  onLaunchWizard: (prompt: string, context: { industry: IndustryType | null, mode: AppComplexityMode | null, capabilities: AICapabilityType[] }) => void;
  selectedIndustry?: IndustryType | null;
  selectedMode?: AppComplexityMode | null;
  selectedCapabilities?: AICapabilityType[];
}

type GenerationMode = 'NEW_IDEA' | 'REDESIGN_SITE';

export function FloatingActionBar({
  requirement,
  onRequirementChange,
  onLaunchWizard,
  selectedIndustry,
  selectedMode,
  selectedCapabilities
}: FloatingActionBarProps) {
  const [isVisible, setIsVisible] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const [generationMode, setGenerationMode] = useState<GenerationMode>('NEW_IDEA');
  const [url, setUrl] = useState("");

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

  if (!isVisible) return null;

  return (
    <div 
      className={cn(
        "fixed top-0 left-0 right-0 z-[100] flex justify-center pt-4 px-4 transition-all duration-300 transform",
        isVisible ? "translate-y-0 opacity-100" : "-translate-y-full opacity-0"
      )}
    >
      <div 
        className={cn(
          "w-full bg-white/90 dark:bg-slate-900/90 backdrop-blur-md border border-slate-200 dark:border-slate-700 shadow-xl rounded-2xl transition-all duration-300 overflow-hidden flex flex-col",
          isExpanded ? "max-w-4xl" : "max-w-3xl"
        )}
      >
        <div className="flex items-start gap-2 p-2">
            {/* Mode Switcher */}
            <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="h-9 gap-1 px-2 text-muted-foreground hover:text-foreground shrink-0">
                {generationMode === 'NEW_IDEA' ? (
                    <>
                    <Sparkles className="w-4 h-4 text-amber-500" />
                    <span className="hidden sm:inline">创造模式</span>
                    </>
                ) : (
                    <>
                    <Wand2 className="w-4 h-4 text-purple-500" />
                    <span className="hidden sm:inline">重构旧站</span>
                    </>
                )}
                <ChevronDown className="w-3 h-3 opacity-50" />
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
                <DropdownMenuItem onClick={() => setGenerationMode('NEW_IDEA')}>
                <Sparkles className="w-4 h-4 mr-2 text-amber-500" />
                创造模式
                {generationMode === 'NEW_IDEA' && <Check className="w-3 h-3 ml-auto" />}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => setGenerationMode('REDESIGN_SITE')}>
                <Wand2 className="w-4 h-4 mr-2 text-purple-500" />
                重构旧站
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
                        placeholder="输入旧网站链接 (https://...)"
                        className="w-full pl-8 pr-3 py-1.5 h-9 rounded-md border border-slate-200 dark:border-slate-700 bg-white/50 dark:bg-slate-900/50 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50"
                    />
                </div>
            )}
            <Textarea
                value={requirement}
                onChange={(e) => onRequirementChange(e.target.value)}
                placeholder={generationMode === 'REDESIGN_SITE' ? "请输入重构需求..." : "在此输入您的创意..."}
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
                    生成
                </Button>
           </div>
        </div>
        
        {/* Expanded Content (Optional: Add more controls here if needed) */}
        {isExpanded && (
             <div className="px-4 pb-3 pt-0 text-xs text-slate-400 flex justify-between items-center border-t border-slate-100 dark:border-slate-800/50 mt-2">
                 <span>已输入 {requirement.length} 字符</span>
                 <span className="flex items-center gap-1">
                     <ArrowUp className="w-3 h-3" /> 
                     滚动��顶部查看完整配置
                 </span>
             </div>
        )}
      </div>
    </div>
  );
}
