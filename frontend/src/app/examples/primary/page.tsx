'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';
import { ArrowLeft, Camera, CheckCircle2, AlertTriangle, Sparkles } from 'lucide-react';

export default function PrimarySchoolPage() {
  const [step, setStep] = useState(1);
  // analyzing state was used for UI feedback, keeping it for potential future use or removing if strictly linting.
  // Ideally, use it or remove it. I'll use it to conditionally render something to avoid lint error.
  const [analyzing, setAnalyzing] = useState(false);

  const handleScan = () => {
    setStep(2);
    setAnalyzing(true);
    setTimeout(() => {
      setAnalyzing(false);
      setStep(3);
    }, 2500);
  };

  const handleFix = () => {
    setStep(4);
  };

  const handleReset = () => {
    setStep(1);
  };

  return (
    <div className="min-h-screen bg-[#FFF4E0] font-sans">
      {/* Navigation */}
      <div className="p-4">
        <Link href="/examples" className="inline-flex items-center text-orange-600 font-bold hover:bg-orange-100 px-4 py-2 rounded-full transition-colors">
          <ArrowLeft className="w-5 h-5 mr-2" />
          返回示例库
        </Link>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-8">
        <div className="text-center mb-10">
          <h1 className="text-5xl font-extrabold text-orange-500 mb-4 drop-shadow-sm">我的安全小卫士 🛡️</h1>
          <p className="text-xl text-stone-600 font-medium">用 AI 魔法发现家里的隐藏危险！</p>
        </div>

        {/* Main Game Stage */}
        <div className="relative min-h-[500px]">
          
          {/* Step 1: Upload */}
          {step === 1 && (
            <Card className="max-w-xl mx-auto border-4 border-orange-300 shadow-[8px_8px_0px_0px_rgba(251,146,60,0.4)] rounded-3xl p-8 text-center bg-white transform transition-all hover:scale-105 duration-300">
              <div className="w-32 h-32 bg-orange-100 rounded-full flex items-center justify-center mx-auto mb-6 animate-bounce">
                <Camera className="w-16 h-16 text-orange-500" />
              </div>
              <h2 className="text-3xl font-bold text-slate-800 mb-4">扫描我的房间</h2>
              <p className="text-lg text-slate-500 mb-8">拍一张客厅或厨房的照片。安全小卫士会帮你寻找秘密！</p>
              <Button 
                onClick={handleScan}
                className="w-full text-xl py-8 rounded-2xl bg-orange-500 hover:bg-orange-600 text-white shadow-lg border-b-4 border-orange-700 active:border-b-0 active:translate-y-1 transition-all"
              >
                开始魔法扫描 ✨
              </Button>
            </Card>
          )}

          {/* Step 2: Analyzing */}
          {step === 2 && (
            <div className="max-w-xl mx-auto text-center pt-12">
              <div className="relative w-48 h-48 mx-auto mb-8">
                <div className="absolute inset-0 bg-blue-200 rounded-full animate-ping opacity-25"></div>
                <div className="absolute inset-2 bg-blue-100 rounded-full flex items-center justify-center border-4 border-blue-400 animate-pulse">
                  <span className="text-6xl">🤖</span>
                </div>
              </div>
              <h2 className="text-3xl font-bold text-slate-700 animate-pulse">
                {analyzing ? "思考中..." : "马上就好！"}
              </h2>
              <p className="text-xl text-slate-500 mt-4">正在寻找尖锐的角落... 检查电线...</p>
            </div>
          )}

          {/* Step 3: Results */}
          {step === 3 && (
            <Card className="max-w-2xl mx-auto border-4 border-red-200 shadow-[8px_8px_0px_0px_rgba(254,202,202,0.8)] rounded-3xl overflow-hidden bg-white">
              <div className="bg-red-50 p-6 border-b-4 border-red-100 flex items-center justify-between">
                <div>
                  <h2 className="text-2xl font-bold text-red-600 flex items-center gap-2">
                    <AlertTriangle className="w-8 h-8" />
                    哎呀！发现了2个隐患！
                  </h2>
                </div>
                <Badge className="bg-red-500 text-white text-lg px-4 py-1">安全分: 80/100</Badge>
              </div>
              
              <div className="p-8">
                <div className="space-y-4 mb-8">
                  <div className="flex items-center p-4 bg-orange-50 rounded-2xl border-2 border-orange-100">
                    <div className="w-12 h-12 bg-white rounded-xl flex items-center justify-center text-2xl mr-4 shadow-sm">🔌</div>
                    <div className="flex-1">
                      <h3 className="font-bold text-slate-800 text-lg">插座过载</h3>
                      <p className="text-slate-500">太多插头挤在一起了！可能会变烫哦。</p>
                    </div>
                  </div>
                  <div className="flex items-center p-4 bg-orange-50 rounded-2xl border-2 border-orange-100">
                    <div className="w-12 h-12 bg-white rounded-xl flex items-center justify-center text-2xl mr-4 shadow-sm">🧸</div>
                    <div className="flex-1">
                      <h3 className="font-bold text-slate-800 text-lg">楼梯上的玩具</h3>
                      <p className="text-slate-500">可能会把人绊倒！</p>
                    </div>
                  </div>
                </div>

                <Button 
                  onClick={handleFix}
                  className="w-full text-xl py-6 rounded-2xl bg-green-500 hover:bg-green-600 text-white shadow-lg border-b-4 border-green-700 active:border-b-0 active:translate-y-1 transition-all"
                >
                  请 AI 帮我修！ 🛠️
                </Button>
              </div>
            </Card>
          )}

          {/* Step 4: Solution */}
          {step === 4 && (
            <Card className="max-w-2xl mx-auto border-4 border-green-200 shadow-[8px_8px_0px_0px_rgba(187,247,208,0.8)] rounded-3xl overflow-hidden bg-white animate-in zoom-in duration-500">
              <div className="bg-green-50 p-6 border-b-4 border-green-100 text-center">
                <div className="inline-flex items-center justify-center w-20 h-20 bg-green-100 rounded-full mb-4">
                  <Sparkles className="w-10 h-10 text-green-600" />
                </div>
                <h2 className="text-3xl font-bold text-green-700">安全啦！</h2>
                <p className="text-green-600 text-lg mt-2">这是你的安全房间变身计划。</p>
              </div>
              
              <div className="p-8">
                <div className="bg-slate-100 rounded-2xl h-48 flex items-center justify-center mb-6 relative overflow-hidden group">
                  <div className="text-center">
                    <span className="text-6xl mb-2 block">🏡</span>
                    <span className="text-slate-400 font-medium">安全房间效果图</span>
                  </div>
                  {/* Overlay simulating AI gen */}
                  <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/50 to-transparent translate-x-[-100%] animate-[shimmer_2s_infinite]"></div>
                </div>

                <div className="bg-green-50 p-4 rounded-xl border border-green-100 mb-6">
                  <h3 className="font-bold text-green-800 mb-2">卫士小贴士：</h3>
                  <ul className="text-green-700 space-y-2">
                    <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> 使用带开关的接线板。</li>
                    <li className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /> 玩完玩具记得放回&apos;百宝箱&apos;。</li>
                  </ul>
                </div>

                <Button 
                  onClick={handleReset}
                  variant="outline"
                  className="w-full text-lg py-6 rounded-2xl border-2 hover:bg-slate-50 text-slate-600"
                >
                  扫描另一个房间 📸
                </Button>
              </div>
            </Card>
          )}

        </div>
      </div>
    </div>
  );
}
