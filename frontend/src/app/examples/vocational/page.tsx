'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';
import { ArrowLeft, HardHat, ClipboardCheck, FileText, AlertTriangle, Eye, Download } from 'lucide-react';

export default function VocationalPage() {
  const [violations] = useState([
    { id: 'V-102', worker: 'Unknown', zone: 'Zone B (Scaffolding)', issue: 'No Helmet Detected', time: '10:42 AM', status: 'Open' },
    { id: 'V-101', worker: 'ID-4592', zone: 'Zone A (Gate)', issue: 'High Vest Missing', time: '09:15 AM', status: 'Resolved' },
  ]);

  return (
    <div className="min-h-screen bg-slate-100 font-sans text-slate-900">
      {/* Professional Header */}
      <header className="bg-white border-b border-slate-200 px-8 py-4 sticky top-0 z-20 shadow-sm">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-8">
            <Link href="/examples" className="text-slate-500 hover:text-slate-800 transition-colors">
              <ArrowLeft className="w-5 h-5" />
            </Link>
            <div className="flex items-center gap-3 border-r border-slate-200 pr-8">
              <div className="bg-orange-500 p-2 rounded text-white">
                <HardHat className="w-6 h-6" />
              </div>
              <div>
                <h1 className="font-bold text-xl leading-none text-slate-900">SiteSafe Pro</h1>
                <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mt-1">Construction Safety AI</p>
              </div>
            </div>
            <nav className="hidden md:flex gap-6 text-sm font-medium text-slate-600">
              <a href="#" className="text-orange-600 border-b-2 border-orange-500 pb-5 -mb-5">Dashboard</a>
              <a href="#" className="hover:text-slate-900">Inspections</a>
              <a href="#" className="hover:text-slate-900">Reports</a>
              <a href="#" className="hover:text-slate-900">Personnel</a>
            </nav>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <div className="text-xs text-slate-500">Site Manager</div>
              <div className="font-bold text-sm">Alex Chen</div>
            </div>
            <div className="w-10 h-10 bg-slate-200 rounded-full border-2 border-white shadow-sm"></div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto p-8 space-y-8">
        
        {/* Top KPIs */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <Card className="p-6 bg-white border-slate-200 shadow-sm">
            <div className="text-slate-500 text-sm font-medium mb-2">Safety Compliance Score</div>
            <div className="text-4xl font-bold text-green-600">98.5%</div>
            <div className="text-xs text-green-600 mt-2 flex items-center gap-1">
              <span className="font-bold">↑ 2.1%</span> vs last week
            </div>
          </Card>
          <Card className="p-6 bg-white border-slate-200 shadow-sm">
             <div className="text-slate-500 text-sm font-medium mb-2">Active Workers</div>
            <div className="text-4xl font-bold text-slate-800">142</div>
            <div className="text-xs text-slate-500 mt-2">Zone A: 45 | Zone B: 97</div>
          </Card>
          <Card className="p-6 bg-white border-slate-200 shadow-sm">
             <div className="text-slate-500 text-sm font-medium mb-2">Open Violations</div>
            <div className="text-4xl font-bold text-orange-600">3</div>
            <div className="text-xs text-orange-600 mt-2 font-medium">Action Required</div>
          </Card>
          <Card className="p-6 bg-gradient-to-br from-slate-900 to-slate-800 text-white shadow-sm border-none">
             <div className="text-slate-300 text-sm font-medium mb-2">Days Without Incident</div>
            <div className="text-4xl font-bold text-white">128</div>
            <div className="text-xs text-slate-400 mt-2">Target: 200 Days</div>
          </Card>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          
          {/* Main Feed: PPE Detection */}
          <div className="lg:col-span-2 space-y-6">
            <Card className="bg-white border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-100 flex justify-between items-center bg-slate-50">
                <h3 className="font-bold text-slate-800 flex items-center gap-2">
                  <Eye className="w-4 h-4 text-slate-500" /> Live PPE Monitoring
                </h3>
                <Badge variant="outline" className="bg-red-50 text-red-600 border-red-200 flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-red-600 animate-pulse"></span> LIVE
                </Badge>
              </div>
              <div className="relative aspect-video bg-slate-900">
                <div className="absolute inset-0 flex items-center justify-center text-slate-500">
                  {/* Placeholder for camera feed */}
                  [ Camera Feed: Zone B - Scaffolding ]
                </div>
                
                {/* Simulated Bounding Box */}
                <div className="absolute top-1/4 left-1/3 w-24 h-48 border-2 border-red-500 rounded-sm">
                   <div className="absolute -top-6 left-0 bg-red-500 text-white text-[10px] px-1 font-bold">NO HELMET</div>
                </div>

                 <div className="absolute top-1/3 right-1/4 w-24 h-48 border-2 border-green-500 rounded-sm">
                   <div className="absolute -top-6 left-0 bg-green-500 text-white text-[10px] px-1 font-bold">COMPLIANT</div>
                </div>
              </div>
              <div className="p-4 bg-white">
                <div className="flex gap-4 text-sm">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 bg-green-500 rounded-sm"></div> Helmet
                  </div>
                   <div className="flex items-center gap-2">
                    <div className="w-3 h-3 bg-yellow-500 rounded-sm"></div> Vest
                  </div>
                   <div className="flex items-center gap-2">
                    <div className="w-3 h-3 bg-blue-500 rounded-sm"></div> Harness
                  </div>
                </div>
              </div>
            </Card>

            <Card className="bg-white border-slate-200 shadow-sm">
              <div className="p-4 border-b border-slate-100 flex justify-between items-center">
                <h3 className="font-bold text-slate-800">Violation Log</h3>
                <Button variant="ghost" size="sm" className="text-slate-500">View All</Button>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>ID</TableHead>
                    <TableHead>Location</TableHead>
                    <TableHead>Issue</TableHead>
                    <TableHead>Time</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {violations.map((v) => (
                    <TableRow key={v.id}>
                      <TableCell className="font-mono text-xs text-slate-500">{v.id}</TableCell>
                      <TableCell>{v.zone}</TableCell>
                      <TableCell>
                        <span className="flex items-center gap-2 font-medium text-slate-700">
                           {v.issue.includes('Helmet') && <AlertTriangle className="w-4 h-4 text-orange-500" />}
                           {v.issue}
                        </span>
                      </TableCell>
                      <TableCell className="text-slate-500">{v.time}</TableCell>
                      <TableCell>
                        <Badge variant={v.status === 'Open' ? 'destructive' : 'secondary'} className={v.status === 'Open' ? 'bg-orange-100 text-orange-700 hover:bg-orange-200 border-none' : ''}>
                          {v.status}
                        </Badge>
                      </TableCell>
                       <TableCell>
                        <Button size="sm" variant="outline">Review</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          </div>

          {/* Sidebar: Checklists & Actions */}
          <div className="space-y-6">
            <Card className="bg-white border-slate-200 shadow-sm p-6">
              <h3 className="font-bold text-slate-800 mb-4 flex items-center gap-2">
                <ClipboardCheck className="w-5 h-5 text-slate-500" /> Daily Inspections
              </h3>
              <div className="space-y-3">
                 <div className="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100">
                    <div className="flex items-center gap-3">
                       <div className="w-5 h-5 rounded border-2 border-slate-300"></div>
                       <span className="text-sm font-medium text-slate-700">Crane #04 Stability</span>
                    </div>
                    <Badge variant="outline" className="text-xs">Required</Badge>
                 </div>
                  <div className="flex items-center justify-between p-3 bg-green-50 rounded-lg border border-green-100">
                    <div className="flex items-center gap-3">
                       <div className="w-5 h-5 rounded bg-green-500 flex items-center justify-center text-white text-xs">✓</div>
                       <span className="text-sm font-medium text-slate-700 line-through opacity-50">Perimeter Fence</span>
                    </div>
                    <span className="text-xs text-green-600 font-bold">Done</span>
                 </div>
                 <div className="flex items-center justify-between p-3 bg-green-50 rounded-lg border border-green-100">
                    <div className="flex items-center gap-3">
                       <div className="w-5 h-5 rounded bg-green-500 flex items-center justify-center text-white text-xs">✓</div>
                       <span className="text-sm font-medium text-slate-700 line-through opacity-50">Fire Extinguishers</span>
                    </div>
                    <span className="text-xs text-green-600 font-bold">Done</span>
                 </div>
              </div>
              <Button className="w-full mt-4 bg-slate-900 text-white hover:bg-slate-800">
                 Start New Inspection
              </Button>
            </Card>

            <Card className="bg-white border-slate-200 shadow-sm p-6">
               <h3 className="font-bold text-slate-800 mb-4 flex items-center gap-2">
                <FileText className="w-5 h-5 text-slate-500" /> Automated Reports
              </h3>
              <p className="text-sm text-slate-500 mb-4">
                 AI has analyzed yesterday&apos;s footage and incident logs. The daily safety briefing is ready.
              </p>
              <Button variant="outline" className="w-full border-slate-300 text-slate-700 hover:bg-slate-50">
                 <Download className="w-4 h-4 mr-2" /> Download Daily Brief (PDF)
              </Button>
            </Card>
          </div>

        </div>
      </main>
    </div>
  );
}
