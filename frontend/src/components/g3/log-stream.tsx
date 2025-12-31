"use client";

import { useEffect, useRef } from "react";
import { cn } from "@/lib/utils";
import { ScrollArea } from "@/components/ui/scroll-area";
import { G3LogEntry } from "@/lib/g3/types";
import { Terminal } from "lucide-react";

interface LogStreamProps {
  logs: G3LogEntry[];
  className?: string;
}

export function LogStream({ logs, className }: LogStreamProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    if (scrollRef.current) {
      const scrollContainer = scrollRef.current.querySelector('[data-radix-scroll-area-viewport]');
      if (scrollContainer) {
        scrollContainer.scrollTop = scrollContainer.scrollHeight;
      }
    }
  }, [logs]);

  return (
    <div className={cn("flex flex-col h-full bg-black/95 rounded-lg border border-slate-800 font-mono text-xs", className)}>
      <div className="flex items-center px-4 py-2 border-b border-slate-800 bg-slate-900/50">
        <Terminal className="w-4 h-4 mr-2 text-slate-400" />
        <span className="text-slate-400 font-semibold">SYSTEM LOG</span>
      </div>
      
      <ScrollArea className="flex-1 p-4" ref={scrollRef}>
        <div className="space-y-2">
          {logs.map((log, i) => (
            <div key={i} className="flex gap-3 animate-in fade-in slide-in-from-left-2 duration-300">
              <span className="text-slate-500 shrink-0">
                {new Date(log.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute:'2-digit', second:'2-digit' })}
              </span>
              
              <div className="flex-1 break-all">
                {/* Role Badge */}
                <span className={cn(
                  "mr-2 font-bold uppercase",
                  log.role === 'PLAYER' && "text-blue-400",
                  log.role === 'COACH' && "text-red-500",
                  log.role === 'ARCHITECT' && "text-purple-400",
                  log.role === 'SYSTEM' && "text-green-400",
                )}>
                  [{log.role}]
                </span>
                
                {/* Content */}
                <span className={cn(
                  log.level === 'ERROR' ? "text-red-400" : "text-slate-300",
                  log.level === 'SUCCESS' && "text-green-400 font-bold"
                )}>
                  {log.content}
                </span>
              </div>
            </div>
          ))}
          
          {logs.length === 0 && (
            <div className="text-slate-600 italic">Waiting for G3 Engine to start...</div>
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
