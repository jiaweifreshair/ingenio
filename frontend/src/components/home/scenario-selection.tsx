"use client";

import { 
  ArrowRight,
  CheckCircle2
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { SCENARIO_CONFIGS } from "@/lib/scenario-config";
import { cn } from "@/lib/utils";

interface ScenarioSelectionProps {
  selectedScenarios?: string[];
  onToggleScenario?: (id: string) => void;
}

export function ScenarioSelection({ 
  selectedScenarios = [], 
  onToggleScenario 
}: ScenarioSelectionProps): React.ReactElement {
  
  const handleToggle = (id: string) => {
    if (onToggleScenario) {
      onToggleScenario(id);
    }
  };

  const scenarios = Object.values(SCENARIO_CONFIGS);

  return (
    <section className="py-24 bg-slate-50/50 dark:bg-slate-900/20">
      <div className="container px-6 mx-auto">
        <div className="mx-auto flex max-w-[58rem] flex-col items-center space-y-4 text-center mb-16">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground tracking-tight">
            全行业场景覆盖
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl">
            点击选择您的业务场景，<span className="text-primary font-semibold">AI将自动优化您的需求描述</span>
          </p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 max-w-5xl mx-auto">
          {scenarios.map((scenario) => {
            const isSelected = selectedScenarios.includes(scenario.id);
            const Icon = scenario.icon;
            
            return (
              <Card
                key={scenario.id}
                onClick={() => handleToggle(scenario.id)}
                className={cn(
                  "group relative cursor-pointer overflow-hidden border transition-all duration-300 hover:-translate-y-1",
                  isSelected 
                    ? "border-primary bg-primary/5 shadow-md" 
                    : "border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 hover:shadow-lg"
                )}
              >
                {isSelected && (
                  <div className="absolute top-3 right-3 text-primary animate-in fade-in zoom-in">
                    <CheckCircle2 className="w-5 h-5" />
                  </div>
                )}
                
                <div className="p-6 flex flex-col items-center text-center space-y-4">
                  <div className={cn(
                    "p-4 rounded-2xl transition-transform duration-300 group-hover:scale-110",
                    isSelected ? "bg-primary/20" : "bg-slate-50 dark:bg-slate-800",
                    scenario.color
                  )}>
                    <Icon className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className={cn("text-lg font-bold mb-1", isSelected ? "text-primary" : "text-foreground")}>
                      {scenario.title}
                    </h3>
                    <p className="text-sm text-muted-foreground">
                      {scenario.description}
                    </p>
                  </div>
                  
                  <div className={cn(
                    "absolute inset-x-0 bottom-0 h-1 bg-gradient-to-r from-transparent via-current to-transparent transition-opacity duration-300",
                    isSelected ? "opacity-100 text-primary" : "opacity-0 group-hover:opacity-100 text-primary"
                  )} />
                </div>
              </Card>
            );
          })}
          
          {/* "More" Card */}
          <Card className="group relative cursor-pointer overflow-hidden border-dashed border-slate-300 dark:border-slate-700 bg-transparent hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors">
            <div className="p-6 h-full flex flex-col items-center justify-center text-center space-y-2">
              <div className="p-4 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-400 group-hover:text-foreground transition-colors">
                <ArrowRight className="w-6 h-6" />
              </div>
              <span className="text-sm font-medium text-muted-foreground group-hover:text-foreground transition-colors">
                更多场景
              </span>
            </div>
          </Card>
        </div>
      </div>
    </section>
  );
}
