import { G3Console } from "@/components/g3/g3-console";
// import { TopNav } from "@/components/layout/top-nav";

export default function G3LabPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-200 flex flex-col">
      {/* <TopNav /> */}
      <main className="flex-1 container mx-auto px-4 py-10 flex flex-col items-center justify-start">
        <div className="w-full max-w-6xl space-y-8">
            <div className="text-center space-y-4">
                <h1 className="text-4xl font-bold text-white tracking-tighter">
                    G3 Engine Lab
                </h1>
                <p className="text-slate-400 max-w-2xl mx-auto">
                    这是一个开发中的实验性功能。观察 G3 (Game/Generator/Guard) 引擎如何通过红蓝博弈自动生成高质量代码。
                </p>
            </div>
            
            <G3Console />
        </div>
      </main>
    </div>
  );
}
