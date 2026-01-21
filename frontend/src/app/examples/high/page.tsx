'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';
import { ArrowLeft, Shield, Radio, MapPin, CheckCircle, Cpu, Zap } from 'lucide-react';

export default function HighSchoolPage() {
  const [incidents, setIncidents] = useState([
    { id: 'INC-2024-8901', type: 'Fire Risk', loc: 'Zone A - Industrial Park', status: 'Active', severity: 'High' },
    { id: 'INC-2024-8902', type: 'Traffic Congestion', loc: 'Zone B - Main Bridge', status: 'Monitoring', severity: 'Medium' },
    { id: 'INC-2024-8903', type: 'Power Fluctuation', loc: 'Zone C - Grid 4', status: 'Resolved', severity: 'Low' },
  ]);

  // agents state is used for rendering.
  const [agents] = useState([
    { name: 'Sentinel-Alpha', role: 'Surveillance', status: 'Online', load: 45 },
    { name: 'Medic-Beta', role: 'Emergency Response', status: 'Standby', load: 12 },
    { name: 'Traffic-Gamma', role: 'Flow Control', status: 'Online', load: 89 },
    { name: 'Grid-Delta', role: 'Infrastructure', status: 'Offline', load: 0 },
  ]);

  const [currentTime, setCurrentTime] = useState('');

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date().toLocaleTimeString('en-US', { hour12: false }));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const handleResolve = (id: string) => {
    setIncidents(prev => prev.map(inc => inc.id === id ? { ...inc, status: 'Resolved', severity: 'Low' } : inc));
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans selection:bg-cyan-900 selection:text-cyan-100">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-900/50 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-[1600px] mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <Link href="/examples" className="text-slate-400 hover:text-white transition-colors">
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 rounded bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center shadow-[0_0_15px_rgba(6,182,212,0.5)]">
                <Shield className="w-5 h-5 text-white" />
              </div>
              <h1 className="font-bold text-xl tracking-wide">CITY<span className="text-cyan-400 font-light">BRAIN</span> <span className="text-xs text-slate-500 ml-2 font-mono">v2.1.0</span></h1>
            </div>
          </div>
          <div className="flex items-center gap-6 text-sm font-mono text-cyan-400">
             <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-cyan-500 animate-pulse"></span>
                SYSTEM OPTIMAL
             </div>
             <div className="text-slate-400">{currentTime}</div>
          </div>
        </div>
      </header>

      <main className="max-w-[1600px] mx-auto p-6 grid grid-cols-12 gap-6">
        
        {/* Left Col: Agent Status (3 cols) */}
        <div className="col-span-12 lg:col-span-3 space-y-6">
           <div className="bg-slate-900/50 border border-slate-800 rounded-xl p-5 backdrop-blur-sm">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex items-center gap-2">
                <Cpu className="w-4 h-4" /> Agent Swarm Status
              </h2>
              <div className="space-y-4">
                {agents.map((agent) => (
                   <div key={agent.name} className="group p-3 rounded-lg bg-slate-900 border border-slate-800 hover:border-cyan-500/30 transition-all">
                      <div className="flex justify-between items-start mb-2">
                         <div className="font-mono font-bold text-sm text-cyan-100">{agent.name}</div>
                         <Badge variant="outline" className={`text-[10px] h-5 ${agent.status === 'Online' ? 'bg-green-500/10 text-green-400 border-green-500/20' : agent.status === 'Standby' ? 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20' : 'bg-red-500/10 text-red-400 border-red-500/20'}`}>
                            {agent.status}
                         </Badge>
                      </div>
                      <div className="text-xs text-slate-500 mb-2">{agent.role}</div>
                      <div className="w-full bg-slate-800 h-1 rounded-full overflow-hidden">
                         <div className="bg-cyan-500 h-full transition-all duration-1000" style={{ width: `${agent.load}%` }}></div>
                      </div>
                   </div>
                ))}
              </div>
           </div>

           <div className="bg-slate-900/50 border border-slate-800 rounded-xl p-5">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex items-center gap-2">
                 <Zap className="w-4 h-4" /> System Resources
              </h2>
              <div className="grid grid-cols-2 gap-4">
                 <div className="text-center p-4 bg-slate-900 rounded-lg border border-slate-800">
                    <div className="text-2xl font-mono font-bold text-white mb-1">24%</div>
                    <div className="text-xs text-slate-500">CPU Load</div>
                 </div>
                 <div className="text-center p-4 bg-slate-900 rounded-lg border border-slate-800">
                    <div className="text-2xl font-mono font-bold text-white mb-1">1.2TB</div>
                    <div className="text-xs text-slate-500">Data Processed</div>
                 </div>
              </div>
           </div>
        </div>

        {/* Middle Col: Map / Vis (6 cols) */}
        <div className="col-span-12 lg:col-span-6 flex flex-col gap-6">
           {/* Map Placeholder */}
           <div className="flex-1 bg-slate-900 rounded-xl border border-slate-800 relative overflow-hidden group min-h-[400px]">
              {/* Grid Background */}
              <div className="absolute inset-0" style={{ backgroundImage: 'linear-gradient(rgba(34, 211, 238, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px)', backgroundSize: '40px 40px' }}></div>
              
              {/* Radar Sweep */}
              <div className="absolute inset-0 bg-[conic-gradient(from_0deg_at_50%_50%,rgba(6,182,212,0)_0deg,rgba(6,182,212,0.1)_360deg)] animate-[spin_4s_linear_infinite] rounded-full scale-[2] opacity-20"></div>

              {/* Points */}
              <div className="absolute top-1/3 left-1/4">
                 <div className="w-4 h-4 bg-red-500 rounded-full animate-ping absolute opacity-75"></div>
                 <div className="w-4 h-4 bg-red-500 rounded-full relative border-2 border-slate-900"></div>
                 <div className="absolute left-6 top-0 bg-slate-900/80 px-2 py-1 rounded border border-red-500/30 text-xs text-red-400 whitespace-nowrap">Zone A: Fire Risk</div>
              </div>

               <div className="absolute bottom-1/3 right-1/3">
                 <div className="w-3 h-3 bg-yellow-500 rounded-full animate-ping absolute opacity-75"></div>
                 <div className="w-3 h-3 bg-yellow-500 rounded-full relative border-2 border-slate-900"></div>
                 <div className="absolute left-6 top-0 bg-slate-900/80 px-2 py-1 rounded border border-yellow-500/30 text-xs text-yellow-400 whitespace-nowrap">Zone B: Traffic</div>
              </div>
           </div>

           {/* Quick Actions */}
           <div className="grid grid-cols-4 gap-4">
              <Button variant="outline" className="border-slate-700 hover:bg-cyan-900/20 hover:text-cyan-400 hover:border-cyan-500/50 h-12">
                 Global Scan
              </Button>
               <Button variant="outline" className="border-slate-700 hover:bg-cyan-900/20 hover:text-cyan-400 hover:border-cyan-500/50 h-12">
                 Dispatch All
              </Button>
               <Button variant="outline" className="border-slate-700 hover:bg-cyan-900/20 hover:text-cyan-400 hover:border-cyan-500/50 h-12">
                 Generate Report
              </Button>
               <Button variant="outline" className="border-slate-700 hover:bg-red-900/20 hover:text-red-400 hover:border-red-500/50 h-12 text-red-400">
                 Lockdown
              </Button>
           </div>
        </div>

        {/* Right Col: Incident Feed (3 cols) */}
        <div className="col-span-12 lg:col-span-3">
           <div className="bg-slate-900/50 border border-slate-800 rounded-xl p-5 h-full backdrop-blur-sm flex flex-col">
              <h2 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex items-center gap-2">
                 <Radio className="w-4 h-4" /> Incident Feed
              </h2>
              
              <div className="space-y-4 overflow-y-auto flex-1 pr-2">
                 {incidents.map((inc) => (
                    <div key={inc.id} className={`p-4 rounded-lg border transition-all ${inc.status === 'Resolved' ? 'bg-slate-900 border-slate-800 opacity-50' : 'bg-slate-800/50 border-slate-700'}`}>
                       <div className="flex justify-between items-start mb-2">
                          <Badge className={`${inc.severity === 'High' ? 'bg-red-500 hover:bg-red-600' : inc.severity === 'Medium' ? 'bg-yellow-500 hover:bg-yellow-600' : 'bg-blue-500 hover:bg-blue-600'} text-white border-none`}>
                             {inc.severity}
                          </Badge>
                          <span className="text-[10px] text-slate-500 font-mono">{inc.id}</span>
                       </div>
                       <h3 className="font-bold text-slate-200 text-sm mb-1">{inc.type}</h3>
                       <div className="flex items-center text-xs text-slate-400 mb-3">
                          <MapPin className="w-3 h-3 mr-1" /> {inc.loc}
                       </div>
                       
                       {inc.status !== 'Resolved' && (
                          <Button size="sm" onClick={() => handleResolve(inc.id)} className="w-full h-7 text-xs bg-slate-700 hover:bg-slate-600 border-slate-600">
                             Resolve Incident
                          </Button>
                       )}
                       {inc.status === 'Resolved' && (
                          <div className="flex items-center justify-center text-xs text-green-500 font-bold gap-1 mt-2">
                             <CheckCircle className="w-3 h-3" /> RESOLVED
                          </div>
                       )}
                    </div>
                 ))}
              </div>
           </div>
        </div>

      </main>
    </div>
  );
}
