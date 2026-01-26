import Link from 'next/link';

export default function ExamplesPage() {
  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-16">
          <h1 className="text-4xl font-bold text-slate-900 mb-4">Ingenio（妙构）挑战赛标杆 · TSX 示例</h1>
          <p className="text-xl text-slate-600">
            对应《智能生成式应用程序设计挑战赛》四个组别的页面能力示例（可作为 UI 对照与验收基准）。
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Primary School */}
          <Link href="/examples/primary" className="group block" target="_blank">
            <div className="bg-white rounded-2xl shadow-sm hover:shadow-xl transition-all duration-300 overflow-hidden border border-slate-100 h-full">
              <div className="bg-yellow-100 h-48 flex items-center justify-center group-hover:bg-yellow-200 transition-colors">
                <span className="text-6xl">🛡️</span>
              </div>
              <div className="p-8">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-2xl font-bold text-slate-800">小学组</h2>
                  <span className="px-3 py-1 bg-yellow-100 text-yellow-800 rounded-full text-sm font-medium">从想法到现实</span>
                </div>
                <h3 className="text-xl font-semibold text-slate-900 mb-2">我的安全小卫士</h3>
                <p className="text-slate-600">
                  强调趣味性、直观性与线性流程：拍照 → AI 识别 → 提示修复的游戏化体验。
                </p>
              </div>
            </div>
          </Link>

          {/* Middle School */}
          <Link href="/examples/middle" className="group block" target="_blank">
            <div className="bg-white rounded-2xl shadow-sm hover:shadow-xl transition-all duration-300 overflow-hidden border border-slate-100 h-full">
              <div className="bg-blue-100 h-48 flex items-center justify-center group-hover:bg-blue-200 transition-colors">
                <span className="text-6xl">🧩</span>
              </div>
              <div className="p-8">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-2xl font-bold text-slate-800">初中组</h2>
                  <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">逻辑与流程</span>
                </div>
                <h3 className="text-xl font-semibold text-slate-900 mb-2">校园逻辑哨兵</h3>
                <p className="text-slate-600">
                  强调问题拆解与流程图表达：展示“摄像头 → AI 分析 → 触发报警”的链路。
                </p>
              </div>
            </div>
          </Link>

          {/* High School */}
          <Link href="/examples/high" className="group block" target="_blank">
            <div className="bg-white rounded-2xl shadow-sm hover:shadow-xl transition-all duration-300 overflow-hidden border border-slate-100 h-full">
              <div className="bg-purple-100 h-48 flex items-center justify-center group-hover:bg-purple-200 transition-colors">
                <span className="text-6xl">🧠</span>
              </div>
              <div className="p-8">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-2xl font-bold text-slate-800">高中组</h2>
                  <span className="px-3 py-1 bg-purple-100 text-purple-800 rounded-full text-sm font-medium">系统架构思维</span>
                </div>
                <h3 className="text-xl font-semibold text-slate-900 mb-2">城市应急智慧中枢</h3>
                <p className="text-slate-600">
                  强调分层架构与多智能体调度：城市级事件监控与联动处置大屏。
                </p>
              </div>
            </div>
          </Link>

          {/* Vocational */}
          <Link href="/examples/vocational" className="group block" target="_blank">
            <div className="bg-white rounded-2xl shadow-sm hover:shadow-xl transition-all duration-300 overflow-hidden border border-slate-100 h-full">
              <div className="bg-green-100 h-48 flex items-center justify-center group-hover:bg-green-200 transition-colors">
                <span className="text-6xl">👷</span>
              </div>
              <div className="p-8">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-2xl font-bold text-slate-800">中职组</h2>
                  <span className="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm font-medium">行业应用</span>
                </div>
                <h3 className="text-xl font-semibold text-slate-900 mb-2">工地安全专家</h3>
                <p className="text-slate-600">
                  强调真实岗位场景与流程优化：工地巡检、违章识别、整改闭环与报表。
                </p>
              </div>
            </div>
          </Link>
        </div>
      </div>
    </div>
  );
}
