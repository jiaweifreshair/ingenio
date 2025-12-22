import { TopNav } from "@/components/layout/top-nav";
import { Footer } from "@/components/layout/footer";
import { Mail, MapPin, MessageCircle, Phone } from "lucide-react";

export default function ContactPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <TopNav />
      <main className="flex-1 container mx-auto px-4 py-16 max-w-5xl">
        {/* Header */}
        <div className="text-center mb-16">
          <h1 className="text-4xl md:text-5xl font-bold mb-6 text-foreground tracking-tight">联系我们</h1>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
            无论您是有产品疑问、商务合作意向，还是仅仅想聊聊 AI 的未来，我们都随时欢迎。
          </p>
        </div>

        {/* Contact Cards */}
        <div className="grid gap-8 md:grid-cols-3 mb-16">
          {/* Customer Support */}
          <div className="flex flex-col items-center p-8 bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-all text-center group">
            <div className="w-14 h-14 bg-blue-100 dark:bg-blue-900/30 text-blue-600 rounded-2xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <MessageCircle className="w-7 h-7" />
            </div>
            <h2 className="text-xl font-bold mb-3">客户支持</h2>
            <p className="text-muted-foreground mb-6 text-sm leading-relaxed">
              遇到产品使用问题？<br/>我们的技术团队将在 24 小时内回复。
            </p>
            <a href="mailto:support@ingenio.ai" className="text-blue-600 font-medium hover:underline">
              support@ingenio.ai
            </a>
          </div>

          {/* Business */}
          <div className="flex flex-col items-center p-8 bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-all text-center group">
            <div className="w-14 h-14 bg-purple-100 dark:bg-purple-900/30 text-purple-600 rounded-2xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <Mail className="w-7 h-7" />
            </div>
            <h2 className="text-xl font-bold mb-3">商务合作</h2>
            <p className="text-muted-foreground mb-6 text-sm leading-relaxed">
              企业定制、API 接入或战略合作，<br/>请联系我们的商务部门。
            </p>
            <a href="mailto:business@ingenio.ai" className="text-purple-600 font-medium hover:underline">
              business@ingenio.ai
            </a>
          </div>

           {/* Phone/Office */}
           <div className="flex flex-col items-center p-8 bg-white dark:bg-slate-900 rounded-3xl border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-all text-center group">
            <div className="w-14 h-14 bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 rounded-2xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
              <Phone className="w-7 h-7" />
            </div>
            <h2 className="text-xl font-bold mb-3">电话咨询</h2>
            <p className="text-muted-foreground mb-6 text-sm leading-relaxed">
              工作日 9:30 - 18:30<br/>期待您的来电。
            </p>
            <a href="tel:+8602112345678" className="text-emerald-600 font-medium hover:underline">
              021-1234-5678
            </a>
          </div>
        </div>

        {/* Office Location Section */}
        <div className="bg-slate-50 dark:bg-slate-900/50 rounded-[2.5rem] p-8 md:p-12 flex flex-col md:flex-row items-center gap-12 border border-slate-200 dark:border-slate-800">
          <div className="flex-1 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-slate-200 dark:bg-slate-800 text-xs font-semibold text-slate-600 dark:text-slate-300 uppercase tracking-wider">
              <MapPin className="w-3 h-3" /> 总部地址
            </div>
            <h2 className="text-3xl font-bold text-foreground">来访指引</h2>
            <p className="text-muted-foreground leading-relaxed text-lg">
              中国上海市浦东新区张江高科技园区<br/>
              博云路 2 号，浦东软件园 3 号楼 501 室
            </p>
            <div className="pt-4">
              <p className="text-sm text-slate-500 mb-1">交通方式：</p>
              <p className="text-sm font-medium text-foreground">地铁 2 号线金科路站 3 号口出，步行 500 米即达。</p>
            </div>
          </div>
          
          {/* Decorative Map Placeholder */}
          <div className="flex-1 w-full h-64 md:h-80 rounded-2xl bg-slate-200 dark:bg-slate-800 overflow-hidden relative group">
             {/* Abstract Map UI */}
             <div className="absolute inset-0 opacity-50 dark:opacity-20 pattern-grid-lg" />
             <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 flex flex-col items-center">
                <div className="relative">
                  <div className="w-4 h-4 bg-red-500 rounded-full animate-ping absolute inset-0 opacity-75"></div>
                  <div className="w-4 h-4 bg-red-600 rounded-full relative z-10 border-2 border-white dark:border-slate-900"></div>
                </div>
                <div className="mt-2 bg-white dark:bg-slate-950 px-3 py-1.5 rounded-lg shadow-lg text-xs font-bold border border-slate-100 dark:border-slate-800 whitespace-nowrap">
                  秒构 AI 创新中心
                </div>
             </div>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}