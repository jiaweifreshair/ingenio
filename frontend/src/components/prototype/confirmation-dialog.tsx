'use client';

import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import type { G3LogEntry } from '@/types/g3';
import { G3LogViewerSimple } from '@/components/generation/G3LogViewer';

interface ConfirmationDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  loading?: boolean;
  g3Logs?: G3LogEntry[];
}

export function ConfirmationDialog({
  isOpen,
  onClose,
  onConfirm,
  loading = false,
  g3Logs = [],
}: ConfirmationDialogProps) {
  const isG3Running = loading && g3Logs.length > 0;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => {
      if (isG3Running) return; // Prevent closing during G3 execution
      if (!open) onClose();
    }}>
      <DialogContent className={isG3Running ? "sm:max-w-[800px] transition-all duration-500" : "sm:max-w-[425px] transition-all duration-500"}>
        {isG3Running ? (
          <div className="space-y-4">
             <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-xl text-blue-500">
                <span className="relative flex h-3 w-3">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-3 w-3 bg-blue-500"></span>
                </span>
                G3 智能引擎运行中
              </DialogTitle>
              <DialogDescription>
                正在进行红蓝博弈与代码自修复，请勿关闭页面...
              </DialogDescription>
            </DialogHeader>
            <div className="py-2">
               <G3LogViewerSimple logs={g3Logs} />
            </div>
          </div>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-xl">
                <AlertTriangle className="h-5 w-5 text-amber-500" />
                确认设计方案？
              </DialogTitle>
              <DialogDescription className="pt-2">
                确认后，系统将锁定当前设计方案，并开始生成全栈代码（前端、后端及数据库）。此过程不可逆，生成期间请勿关闭页面。
              </DialogDescription>
            </DialogHeader>
            <div className="py-4">
              <div className="rounded-md bg-blue-50 p-4">
                <div className="flex">
                  <div className="flex-shrink-0">
                    <CheckCircle2 className="h-5 w-5 text-blue-400" aria-hidden="true" />
                  </div>
                  <div className="ml-3">
                    <h3 className="text-sm font-medium text-blue-800">即将执行的操作</h3>
                    <div className="mt-2 text-sm text-blue-700">
                      <ul className="list-disc pl-5 space-y-1">
                        <li>生成前后端代码结构</li>
                        <li>配置数据库 Schema</li>
                        <li>集成所选 AI 能力</li>
                        <li>生成多端适配代码</li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <DialogFooter className="gap-2 sm:gap-0">
              <Button variant="outline" onClick={onClose} disabled={loading}>
                再看看
              </Button>
              <Button onClick={onConfirm} disabled={loading} className="bg-gradient-to-r from-purple-600 to-blue-600 text-white">
                {loading ? '准备中...' : '确认并生成'}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
