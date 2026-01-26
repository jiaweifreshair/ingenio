'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import Link from 'next/link';
import { ArrowLeft, Play, RotateCcw, Activity, Users, Bell, Camera, Server, ArrowRight } from 'lucide-react';

export default function MiddleSchoolPage() {
  const [isRunning, setIsRunning] = useState(false);
  const [log, setLog] = useState<string[]>([]);
  const [activeNode, setActiveNode] = useState<number | null>(null);

  const runSimulation = () => {
    setIsRunning(true);
    setLog([]);
    setActiveNode(1);
    addLog("系统：启动逻辑流...");

    setTimeout(() => {
      setActiveNode(1);
      addLog("输入：体育馆摄像头已激活。正在获取画面...");
    }, 500);

    setTimeout(() => {
      setActiveNode(2);
      addLog("处理：正在分析人群密度...");
      addLog("处理：检测到 45 人（容量上限：40）。");
    }, 2000);

    setTimeout(() => {
      setActiveNode(3);
      addLog("逻辑：超过阈值（45 > 40）。触发报警路径。");
    }, 4000);

    setTimeout(() => {
      setActiveNode(4);
      addLog("输出：正在通过体育馆广播播放警告。");
      addLog("输出：正在向值班老师发送短信。");
      setIsRunning(false);
    }, 6000);
  };

  const addLog = (msg: string) => {
    setLog(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  return (
    <div className="min-h-screen bg-slate-50 font-sans text-slate-900">
      {/* Navbar */}
      <nav className="bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-4">
          <Link href="/examples" className="text-slate-500 hover:text-blue-600 transition-colors">
            <ArrowLeft className="w-5 h-5" />
          </Link>
          <div className="flex items-center gap-3">
            <div className="bg-blue-600 text-white p-2 rounded-lg">
              <Activity className="w-5 h-5" />
            </div>
            <div>
              <h1 className="font-bold text-lg leading-tight">校园逻辑哨兵</h1>
              <p className="text-xs text-slate-500">项目：体育馆拥挤检测卫士</p>
            </div>
          </div>
        </div>
        <div className="flex gap-2">
           <Button variant="outline" onClick={() => { setIsRunning(false); setLog([]); setActiveNode(null); }}>
            <RotateCcw className="w-4 h-4 mr-2" /> 重置
          </Button>
          <Button onClick={runSimulation} disabled={isRunning} className="bg-blue-600 hover:bg-blue-700">
            <Play className="w-4 h-4 mr-2" /> {isRunning ? '运行中...' : '运行模拟'}
          </Button>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto p-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left: Logic Canvas */}
        <div className="lg:col-span-2 space-y-4">
          <Card className="p-1 bg-slate-100 border-slate-200 h-[600px] relative overflow-hidden">
             <div className="absolute inset-0 grid grid-cols-[repeat(20,minmax(0,1fr))] grid-rows-[repeat(20,minmax(0,1fr))] opacity-10 pointer-events-none">
                {Array.from({ length: 400 }).map((_, i) => (
                  <div key={i} className="border-[0.5px] border-slate-400"></div>
                ))}
             </div>
             
             {/* Flow Diagram */}
             <div className="relative h-full w-full p-10 flex flex-col justify-center items-center gap-16">
                
                {/* Node 1: Input */}
                <div className={`relative z-10 w-64 p-4 rounded-xl border-2 transition-all duration-500 ${activeNode === 1 ? 'bg-white border-blue-500 shadow-[0_0_20px_rgba(59,130,246,0.5)] scale-105' : 'bg-white border-slate-200 shadow-sm'}`}>
                  <div className="flex items-center gap-3 mb-2">
                    <div className="p-2 bg-purple-100 text-purple-600 rounded-lg"><Camera className="w-5 h-5"/></div>
                    <span className="font-bold text-slate-700">输入：体育馆摄像头</span>
                  </div>
                  <div className="text-xs text-slate-500 bg-slate-50 p-2 rounded">状态：在线<br/>分辨率：1080p</div>
                  
                  {/* Connector Line Vertical */}
                  <div className="absolute left-1/2 -bottom-16 w-0.5 h-16 bg-slate-300 -ml-[1px]">
                     {activeNode === 1 && <div className="absolute top-0 left-0 w-full h-1/2 bg-blue-500 animate-[flow_1s_infinite]"></div>}
                  </div>
                  <div className="absolute left-1/2 -bottom-2 transform -translate-x-1/2 translate-y-full">
                    <ArrowRight className="w-4 h-4 text-slate-300 rotate-90" />
                  </div>
                </div>

                {/* Node 2: Process */}
                <div className={`relative z-10 w-64 p-4 rounded-xl border-2 transition-all duration-500 ${activeNode === 2 ? 'bg-white border-blue-500 shadow-[0_0_20px_rgba(59,130,246,0.5)] scale-105' : 'bg-white border-slate-200 shadow-sm'}`}>
                   <div className="flex items-center gap-3 mb-2">
                    <div className="p-2 bg-yellow-100 text-yellow-600 rounded-lg"><Users className="w-5 h-5"/></div>
                    <span className="font-bold text-slate-700">AI：人群计数器</span>
                  </div>
                   <div className="text-xs text-slate-500 bg-slate-50 p-2 rounded">模型：YOLOv8<br/>置信度：98%</div>

                   {/* Connector Line Vertical */}
                   <div className="absolute left-1/2 -bottom-16 w-0.5 h-16 bg-slate-300 -ml-[1px]"></div>
                   <div className="absolute left-1/2 -bottom-2 transform -translate-x-1/2 translate-y-full">
                    <ArrowRight className="w-4 h-4 text-slate-300 rotate-90" />
                  </div>
                </div>

                {/* Node 3: Logic Gate */}
                <div className={`relative z-10 w-48 p-3 rounded-full border-2 text-center transition-all duration-500 ${activeNode === 3 ? 'bg-white border-blue-500 shadow-[0_0_20px_rgba(59,130,246,0.5)] scale-105' : 'bg-white border-slate-200 shadow-sm'}`}>
                   <span className="font-mono font-bold text-slate-700 text-sm">IF 人数 &gt; 40</span>
                   
                   {/* Connector Line Vertical */}
                   <div className="absolute left-1/2 -bottom-16 w-0.5 h-16 bg-slate-300 -ml-[1px]"></div>
                   <div className="absolute left-1/2 -bottom-2 transform -translate-x-1/2 translate-y-full">
                    <ArrowRight className="w-4 h-4 text-slate-300 rotate-90" />
                  </div>
                </div>

                {/* Node 4: Output */}
                <div className={`relative z-10 w-64 p-4 rounded-xl border-2 transition-all duration-500 ${activeNode === 4 ? 'bg-red-50 border-red-500 shadow-[0_0_20px_rgba(239,68,68,0.5)] scale-105' : 'bg-white border-slate-200 shadow-sm'}`}>
                   <div className="flex items-center gap-3 mb-2">
                    <div className="p-2 bg-red-100 text-red-600 rounded-lg"><Bell className="w-5 h-5"/></div>
                    <span className="font-bold text-slate-700">动作：报警</span>
                  </div>
                   <div className="text-xs text-slate-500 bg-slate-50 p-2 rounded">目标：广播，短信<br/>优先级：高</div>
                </div>

             </div>
          </Card>
        </div>

        {/* Right: Module Palette & Log */}
        <div className="space-y-6">
          <Card className="p-6">
            <h3 className="font-semibold text-slate-900 mb-4 flex items-center gap-2">
              <Server className="w-4 h-4 text-slate-500" /> 系统日志
            </h3>
            <div className="bg-slate-950 rounded-lg p-4 h-[300px] overflow-y-auto font-mono text-xs text-green-400">
              {log.length === 0 ? (
                <span className="text-slate-600">准备就绪，等待模拟...</span>
              ) : (
                log.map((l, i) => <div key={i} className="mb-1">{l}</div>)
              )}
              {isRunning && <div className="animate-pulse">_</div>}
            </div>
          </Card>

          <Card className="p-6">
            <h3 className="font-semibold text-slate-900 mb-4">组件库</h3>
            <div className="grid grid-cols-2 gap-3">
               <div className="p-3 bg-white border border-slate-200 rounded-lg text-center hover:border-blue-400 cursor-grab active:cursor-grabbing">
                  <div className="text-2xl mb-1">📷</div>
                  <div className="text-xs font-medium">摄像头</div>
               </div>
               <div className="p-3 bg-white border border-slate-200 rounded-lg text-center hover:border-blue-400 cursor-grab active:cursor-grabbing">
                  <div className="text-2xl mb-1">🌡️</div>
                  <div className="text-xs font-medium">温度传感器</div>
               </div>
               <div className="p-3 bg-white border border-slate-200 rounded-lg text-center hover:border-blue-400 cursor-grab active:cursor-grabbing">
                  <div className="text-2xl mb-1">🧠</div>
                  <div className="text-xs font-medium">AI 分析</div>
               </div>
               <div className="p-3 bg-white border border-slate-200 rounded-lg text-center hover:border-blue-400 cursor-grab active:cursor-grabbing">
                  <div className="text-2xl mb-1">📢</div>
                  <div className="text-xs font-medium">广播</div>
               </div>
            </div>
          </Card>
        </div>

      </main>
    </div>
  );
}
