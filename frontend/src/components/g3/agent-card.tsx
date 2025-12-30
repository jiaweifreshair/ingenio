"use client";

import { cn } from "@/lib/utils";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Loader2 } from "lucide-react";

interface AgentCardProps {
  role: 'PLAYER' | 'COACH' | 'ARCHITECT';
  status: 'IDLE' | 'THINKING' | 'WORKING' | 'DONE' | 'WAITING';
  name: string;
  description: string;
  className?: string;
}

export function AgentCard({ role, status, name, description, className }: AgentCardProps) {
  
  const colors = {
    PLAYER: "border-blue-500/30 bg-blue-950/10 text-blue-500",
    COACH: "border-red-500/30 bg-red-950/10 text-red-500",
    ARCHITECT: "border-purple-500/30 bg-purple-950/10 text-purple-500",
  };

  const statusColors = {
    IDLE: "bg-slate-500",
    THINKING: "bg-yellow-500 animate-pulse",
    WORKING: "bg-green-500 animate-pulse",
    DONE: "bg-green-500",
    WAITING: "bg-slate-700",
  };

  return (
    <Card className={cn(
      "relative overflow-hidden border-2 transition-all duration-300",
      colors[role],
      status === 'WORKING' || status === 'THINKING' ? "shadow-[0_0_15px_rgba(var(--primary),0.3)] scale-[1.02]" : "opacity-80 grayscale-[0.3]",
      className
    )}>
      {/* Background Pulse Animation */}
      {(status === 'WORKING' || status === 'THINKING') && (
        <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer pointer-events-none" />
      )}

      <div className="p-4 flex flex-col h-full">
        <div className="flex justify-between items-start mb-2">
          <Badge variant="outline" className={cn("font-bold", colors[role], "border-current")}>
            {role}
          </Badge>
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono uppercase text-muted-foreground">{status}</span>
            <div className={cn("w-2 h-2 rounded-full", statusColors[status])} />
          </div>
        </div>

        <h3 className="text-lg font-bold mb-1">{name}</h3>
        <p className="text-xs text-muted-foreground/80 mb-4 flex-1">{description}</p>

        <div className="mt-auto">
          {status === 'THINKING' && (
             <div className="flex items-center text-xs gap-1 opacity-70">
                <Loader2 className="w-3 h-3 animate-spin" />
                Thinking...
             </div>
          )}
          {status === 'WORKING' && (
             <div className="text-xs font-mono opacity-80">
                &gt; Generative Output Stream...
             </div>
          )}
        </div>
      </div>
    </Card>
  );
}
