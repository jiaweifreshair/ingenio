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
    { id: 'V-102', worker: '未知', zone: 'B区 (脚手架)', issue: '未检测到安全帽', time: '10:42 AM', status: '待处理' },
    { id: 'V-101', worker: 'ID-4592', zone: 'A区 (大门)', issue: '未穿反光背心', time: '09:15 AM', status: '已解决' },
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
                <h1 className="font-bold text-xl leading-none text-slate-900">工地安全专家 Pro</h1>
                <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold mt-1">施工安全 AI 助手</p>
              </div>
            </div>
            <nav className="hidden md:flex gap-6 text-sm font-medium text-slate-600">
              <a href="#" className="text-orange-600 border-b-2 border-orange-500 pb-5 -mb-5">仪表盘</a>
              <a href="#" className="hover:text-slate-900">巡检</a>
              <a href="#" className="hover:text-slate-900">报告</a>
              <a href="#" className="hover:text-slate-900">人员</a>
            </nav>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <div className="text-xs text-slate-500">现场经理</div>
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
            <div className="text-slate-500 text-sm font-medium mb-2">安全合规评分</div>
            <div className="text-4xl font-bold text-green-600">98.5%</div>
            <div className="text-xs text-green-600 mt-2 flex items-center gap-1">
              <span className="font-bold">↑ 2.1%</span> 较上周
            </div>
          </Card>
          <Card className="p-6 bg-white border-slate-200 shadow-sm">
             <div className="text-slate-500 text-sm font-medium mb-2">在岗工人数</div>
            <div className="text-4xl font-bold text-slate-800">142</div>
            <div className="text-xs text-slate-500 mt-2">A区: 45 | B区: 97</div>
          </Card>
          <Card className="p-6 bg-white border-slate-200 shadow-sm">
             <div className="text-slate-500 text-sm font-medium mb-2">未结违规</div>
            <div className="text-4xl font-bold text-orange-600">3</div>
            <div className="text-xs text-orange-600 mt-2 font-medium">需立即处理</div>
          </Card>
          <Card className="p-6 bg-gradient-to-br from-slate-900 to-slate-800 text-white shadow-sm border-none">
             <div className="text-slate-300 text-sm font-medium mb-2">安全生产天数</div>
            <div className="text-4xl font-bold text-white">128</div>
            <div className="text-xs text-slate-400 mt-2">目标：200 天</div>
          </Card>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          
          {/* Main Feed: PPE Detection */}
          <div className="lg:col-span-2 space-y-6">
            <Card className="bg-white border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-100 flex justify-between items-center bg-slate-50">
                <h3 className="font-bold text-slate-800 flex items-center gap-2">
                  <Eye className="w-4 h-4 text-slate-500" /> PPE 实时监控
                </h3>
                <Badge variant="outline" className="bg-red-50 text-red-600 border-red-200 flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-red-600 animate-pulse"></span> 直播
                </Badge>
              </div>
              <div className="relative aspect-video bg-slate-900">
                <div className="absolute inset-0 flex items-center justify-center text-slate-500">
                  {/* Placeholder for camera feed */}
                  [ 摄像头画面：B区 - 脚手架 ]
                </div>
                
                {/* Simulated Bounding Box */}
                <div className="absolute top-1/4 left-1/3 w-24 h-48 border-2 border-red-500 rounded-sm">
                   <div className="absolute -top-6 left-0 bg-red-500 text-white text-[10px] px-1 font-bold">未戴安全帽</div>
                </div>

                 <div className="absolute top-1/3 right-1/4 w-24 h-48 border-2 border-green-500 rounded-sm">
                   <div className="absolute -top-6 left-0 bg-green-500 text-white text-[10px] px-1 font-bold">合规</div>
                </div>
              </div>
              <div className="p-4 bg-white">
                <div className="flex gap-4 text-sm">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 bg-green-500 rounded-sm"></div> 安全帽
                  </div>
                   <div className="flex items-center gap-2">
                    <div className="w-3 h-3 bg-yellow-500 rounded-sm"></div> 反光背心
                  </div>
                   <div className="flex items-center gap-2">
                    <div className="w-3 h-3 bg-blue-500 rounded-sm"></div> 安全带
                  </div>
                </div>
              </div>
            </Card>

            <Card className="bg-white border-slate-200 shadow-sm">
              <div className="p-4 border-b border-slate-100 flex justify-between items-center">
                <h3 className="font-bold text-slate-800">违规记录</h3>
                <Button variant="ghost" size="sm" className="text-slate-500">查看全部</Button>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>编号</TableHead>
                    <TableHead>位置</TableHead>
                    <TableHead>问题</TableHead>
                    <TableHead>时间</TableHead>
                    <TableHead>状态</TableHead>
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
                           {v.issue.includes('安全帽') && <AlertTriangle className="w-4 h-4 text-orange-500" />}
                           {v.issue}
                        </span>
                      </TableCell>
                      <TableCell className="text-slate-500">{v.time}</TableCell>
                      <TableCell>
                        <Badge variant={v.status === '待处理' ? 'destructive' : 'secondary'} className={v.status === '待处理' ? 'bg-orange-100 text-orange-700 hover:bg-orange-200 border-none' : ''}>
                          {v.status}
                        </Badge>
                      </TableCell>
                       <TableCell>
                        <Button size="sm" variant="outline">复核</Button>
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
                <ClipboardCheck className="w-5 h-5 text-slate-500" /> 每日巡检
              </h3>
              <div className="space-y-3">
                 <div className="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-100">
                    <div className="flex items-center gap-3">
                       <div className="w-5 h-5 rounded border-2 border-slate-300"></div>
                       <span className="text-sm font-medium text-slate-700">4号塔吊稳定性</span>
                    </div>
                    <Badge variant="outline" className="text-xs">必检</Badge>
                 </div>
                  <div className="flex items-center justify-between p-3 bg-green-50 rounded-lg border border-green-100">
                    <div className="flex items-center gap-3">
                       <div className="w-5 h-5 rounded bg-green-500 flex items-center justify-center text-white text-xs">✓</div>
                       <span className="text-sm font-medium text-slate-700 line-through opacity-50">围挡检查</span>
                    </div>
                    <span className="text-xs text-green-600 font-bold">完成</span>
                 </div>
                 <div className="flex items-center justify-between p-3 bg-green-50 rounded-lg border border-green-100">
                    <div className="flex items-center gap-3">
                       <div className="w-5 h-5 rounded bg-green-500 flex items-center justify-center text-white text-xs">✓</div>
                       <span className="text-sm font-medium text-slate-700 line-through opacity-50">灭火器检查</span>
                    </div>
                    <span className="text-xs text-green-600 font-bold">完成</span>
                 </div>
              </div>
              <Button className="w-full mt-4 bg-slate-900 text-white hover:bg-slate-800">
                 开始新巡检
              </Button>
            </Card>

            <Card className="bg-white border-slate-200 shadow-sm p-6">
               <h3 className="font-bold text-slate-800 mb-4 flex items-center gap-2">
                <FileText className="w-5 h-5 text-slate-500" /> 自动化报告
              </h3>
              <p className="text-sm text-slate-500 mb-4">
                 AI 已分析昨日监控画面与日志。每日安全简报已生成。
              </p>
              <Button variant="outline" className="w-full border-slate-300 text-slate-700 hover:bg-slate-50">
                 <Download className="w-4 h-4 mr-2" /> 下载每日简报 (PDF)
              </Button>
            </Card>
          </div>

        </div>
      </main>
    </div>
  );
}
