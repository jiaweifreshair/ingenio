import React from 'react';
import { G3LogViewer } from '@/components/generation/G3LogViewer';

export const metadata = {
  title: 'Ingenio Laboratory | G3 Engine Monitor',
  description: 'Real-time monitoring of the G3 Software Factory execution.',
};

export default function LabPage() {
  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <div className="container mx-auto py-10 space-y-8 max-w-6xl">
        <div className="flex flex-col space-y-2">
          <div className="flex items-center gap-3">
             <div className="h-8 w-8 rounded-lg bg-indigo-600 flex items-center justify-center">
                <span className="text-white font-bold font-mono">G3</span>
             </div>
             <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
               Ingenio Software Factory
             </h1>
          </div>
          <p className="text-slate-500 dark:text-slate-400 max-w-2xl">
            This dashboard monitors the <strong>G3 Engine</strong> (Game/Generator/Guard) as it autonomously builds software. 
            Currently piloting: <span className="font-semibold text-indigo-600 dark:text-indigo-400">ProductShot AI</span>.
          </p>
        </div>
        
        <div className="grid gap-6">
           {/* Monitor Section */}
           <section>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-200">Production Line Monitor</h2>
                <div className="text-xs font-mono text-slate-400">
                  Target: ProductImageUploader.tsx
                </div>
              </div>
              <G3LogViewer requirement="Build a React component for Product Image Upload with Drag & Drop and Background Removal logic." />
           </section>
           
           {/* Context/Debug Info */}
           <section className="grid md:grid-cols-3 gap-6">
              <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
                 <h3 className="font-semibold text-sm mb-2 text-blue-600">Player Agent (Blue)</h3>
                 <p className="text-xs text-slate-500">
                   Responsible for high-speed code generation and creative solutions. Using <strong>GPT-4o</strong> optimized for React/Shadcn.
                 </p>
              </div>
              <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
                 <h3 className="font-semibold text-sm mb-2 text-red-600">Coach Agent (Red)</h3>
                 <p className="text-xs text-slate-500">
                   Responsible for adversarial testing, security auditing, and edge-case detection. Using <strong>o1-preview</strong> reasoning.
                 </p>
              </div>
              <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
                 <h3 className="font-semibold text-sm mb-2 text-green-600">Executor (Judge)</h3>
                 <p className="text-xs text-slate-500">
                   Deterministic runtime environment. Validates syntax, runs unit tests, and performs OCR/Visual checks on output.
                 </p>
              </div>
           </section>
        </div>
      </div>
    </div>
  );
}
