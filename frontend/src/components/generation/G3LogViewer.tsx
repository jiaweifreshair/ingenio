'use client';

import React, { useEffect, useRef } from 'react';
import { G3LogEntry } from '@/types/g3';

interface G3LogViewerProps {
  logs: G3LogEntry[];
}

export function G3LogViewer({ logs }: G3LogViewerProps) {
  const endRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const getRoleStyle = (role: G3LogEntry['role']) => {
    switch (role) {
      case 'PLAYER': return 'text-blue-400 font-bold';
      case 'COACH': return 'text-red-400 font-bold';
      case 'EXECUTOR': return 'text-yellow-400 font-bold';
      default: return 'text-gray-400';
    }
  };

  const getRoleIcon = (role: G3LogEntry['role']) => {
    switch (role) {
      case 'PLAYER': return '🔵';
      case 'COACH': return '🔴';
      case 'EXECUTOR': return '⚖️';
      default: return '⚪';
    }
  };

  return (
    <div className="bg-gray-900 rounded-lg p-4 font-mono text-sm h-96 overflow-y-auto border border-gray-700 shadow-inner">
      {logs.length === 0 && (
        <div className="text-gray-500 italic text-center mt-32">Waiting for G3 Engine...</div>
      )}
      
      {logs.map((log, index) => (
        <div key={index} className="mb-2 animate-in fade-in slide-in-from-bottom-2 duration-300">
          <span className="opacity-40 mr-2 text-xs">[{log.timestamp}]</span>
          <span className={`${getRoleStyle(log.role)} mr-2`}>
            {getRoleIcon(log.role)} [{log.role}]
          </span>
          <span className={log.level === 'error' ? 'text-red-300' : log.level === 'warn' ? 'text-yellow-200' : 'text-gray-200'}>
            {log.message}
          </span>
        </div>
      ))}
      <div ref={endRef} />
    </div>
  );
}
