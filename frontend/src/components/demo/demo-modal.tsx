/**
 * 演示视频模态框组件
 * 用于展示秒构AI的使用演示
 * 修复HeroBanner中"观看示例"按钮的功能缺失问题
 */
"use client";

import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogTrigger,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Play, Volume2, VolumeX, Maximize, SkipForward, SkipBack } from 'lucide-react';

export function DemoModal() {
  const [isOpen, setIsOpen] = useState(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const duration = 60; // 60秒演示视频（固定时长）

  // 格式化时间显示
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  // 处理播放/暂停
  const handlePlayPause = () => {
    setIsPlaying(!isPlaying);
  };

  // 处理静音切换
  const handleMuteToggle = () => {
    setIsMuted(!isMuted);
  };

  // 处理进度条点击
  const handleProgressClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const percentage = x / rect.width;
    setCurrentTime(Math.floor(percentage * duration));
  };

  // 处理快进/快退
  const handleSkipForward = () => {
    setCurrentTime(prev => Math.min(prev + 10, duration));
  };

  const handleSkipBack = () => {
    setCurrentTime(prev => Math.max(prev - 10, 0));
  };

  const progressPercentage = (currentTime / duration) * 100;

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        <Button size="lg" variant="outline" className="gap-2">
          <Play className="w-4 h-4" />
          观看 1 分钟示例
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-4xl w-full aspect-video p-0 overflow-hidden">
        {/* 为屏幕阅读器提供标题和描述 */}
        <DialogTitle className="sr-only">
          秒构AI 演示视频
        </DialogTitle>
        <DialogDescription className="sr-only">
          观看1分钟的演示视频，了解如何使用秒构AI快速创建AI应用
        </DialogDescription>

        <div className="relative w-full h-full bg-black">
          {/* 模拟视频播放器界面 */}
          <div className="relative w-full h-full">
            {/* 视频区域 */}
            <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-primary/20 via-secondary/20 to-accent/20">
              {/* 模拟视频内容 */}
              <div className="text-center text-white p-8">
                <div className="mb-8">
                  <div className="inline-flex items-center justify-center w-20 h-20 bg-white/20 rounded-full mb-4">
                    <Play className="w-8 h-8 text-white" />
                  </div>
                  <h3 className="text-2xl font-bold mb-2">秒构AI 演示</h3>
                  <p className="text-lg opacity-90">1分钟快速了解如何创建你的第一个应用</p>
                </div>

                {/* 演示步骤预览 */}
                <div className="grid grid-cols-4 gap-4 mt-12 max-w-2xl mx-auto">
                  {[
                    { step: 1, title: "输入需求", desc: "描述你想要的应用" },
                    { step: 2, title: "AI分析", desc: "智能拆解功能模块" },
                    { step: 3, title: "向导填空", desc: "完善应用配置" },
                    { step: 4, title: "发布上线", desc: "一键部署到云端" }
                  ].map((item, index) => (
                    <div key={index} className="bg-white/10 backdrop-blur-sm rounded-lg p-4">
                      <div className="text-2xl font-bold mb-2">{item.step}</div>
                      <div className="text-sm font-medium mb-1">{item.title}</div>
                      <div className="text-xs opacity-75">{item.desc}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* 播放控制覆盖层 */}
              {!isPlaying && (
                <div className="absolute inset-0 flex items-center justify-center">
                  <Button
                    size="lg"
                    onClick={handlePlayPause}
                    className="w-16 h-16 rounded-full bg-white/20 hover:bg-white/30 backdrop-blur-sm border-white/30"
                  >
                    <Play className="w-6 h-6 ml-1" />
                  </Button>
                </div>
              )}
            </div>

            {/* 视频控制栏 */}
            <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent p-4">
              {/* 进度条 */}
              <div
                className="w-full h-1 bg-white/30 rounded-full mb-4 cursor-pointer overflow-hidden"
                onClick={handleProgressClick}
              >
                <div
                  className="h-full bg-primary transition-all duration-100"
                  style={{ width: `${progressPercentage}%` }}
                />
              </div>

              {/* 控制按钮 */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handlePlayPause}
                    className="text-white hover:bg-white/20"
                  >
                    {isPlaying ? (
                      <div className="w-4 h-4 flex items-center justify-center">
                        <div className="w-1 h-4 bg-white mr-1" />
                        <div className="w-1 h-4 bg-white" />
                      </div>
                    ) : (
                      <Play className="w-4 h-4 ml-1" />
                    )}
                  </Button>

                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleSkipBack}
                    className="text-white hover:bg-white/20"
                  >
                    <SkipBack className="w-4 h-4" />
                  </Button>

                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleSkipForward}
                    className="text-white hover:bg-white/20"
                  >
                    <SkipForward className="w-4 h-4" />
                  </Button>

                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleMuteToggle}
                    className="text-white hover:bg-white/20"
                  >
                    {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
                  </Button>

                  <div className="text-white text-sm">
                    {formatTime(currentTime)} / {formatTime(duration)}
                  </div>
                </div>

                <Button
                  variant="ghost"
                  size="sm"
                  className="text-white hover:bg-white/20"
                >
                  <Maximize className="w-4 h-4" />
                </Button>
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}