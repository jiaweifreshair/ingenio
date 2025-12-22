import { TopNav } from "@/components/layout/top-nav";
import { Footer } from "@/components/layout/footer";

export default function TermsPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <TopNav />
      <main className="flex-1 container mx-auto px-4 py-12 max-w-4xl">
        <h1 className="text-3xl md:text-4xl font-bold mb-8 text-foreground">服务条款</h1>
        <div className="prose dark:prose-invert max-w-none text-muted-foreground">
          <p className="mb-8 text-lg leading-relaxed">
            最后更新日期：{new Date().toLocaleDateString()}
          </p>
          <div className="p-6 bg-slate-50 dark:bg-slate-900 rounded-2xl mb-12">
            <p className="mb-0">
              欢迎使用秒构AI（Ingenio）。本协议是您与秒构AI之间关于使用我们提供的AI应用生成及相关服务所订立的法律协议。请您在注册和使用服务前仔细阅读。一旦您使用我们的服务，即表示您已同意接受本条款的约束。
            </p>
          </div>
          
          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">1. 服务内容与账户</h2>
          <ul className="list-disc pl-6 mb-8 space-y-3">
            <li><strong>服务描述：</strong> 秒构AI提供基于人工智能的代码生成、UI设计、云端部署及相关辅助工具。我们致力于提供高质量的生成结果，但鉴于AI技术的概率性特征，我们不保证生成结果完全准确无误或满足所有特殊需求。</li>
            <li><strong>账户安全：</strong> 您需对账户的安全性负责。任何通过您的账户进行的操作均视为您本人的行为。如发现未授权使用，请立即通知我们。</li>
            <li><strong>使用限制：</strong> 您不得利用本服务生成恶意软件、诈骗网站、色情暴力等违反法律法规的内容。我们保留审查和封禁违规账户的权利。</li>
          </ul>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">2. 知识产权声明</h2>
          <ul className="list-disc pl-6 mb-8 space-y-3">
            <li><strong>平台权益：</strong> 秒构AI平台的算法、模型、源代码、界面设计及商标等知识产权均归我们所有。</li>
            <li><strong>用户产出权益：</strong> 您通过付费服务生成的应用程序代码、设计稿、数据库结构等产出物的知识产权归您所有。您可以自由商业化、出售或修改这些产出物。</li>
            <li><strong>免费版限制：</strong> 如果您使用免费版服务，生成的代码可能需要保留&quot;Powered by Ingenio&quot;的署名标识，且部分高级组件可能受开源协议限制。</li>
          </ul>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">3. 付费与订阅</h2>
          <p className="mb-8">
            部分高级功能（如源码下载、私有化部署、团队协作）需要付费订阅。订阅费用将在购买时从您的支付账户扣除。我们会提前通知价格变更，且不影响已生效的订阅周期。退款政策请参阅具体的订阅说明。
          </p>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">4. 免责声明与责任限制</h2>
          <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-6 rounded-xl mb-8">
            <p className="font-semibold text-amber-800 dark:text-amber-200 mb-2">重要提示：</p>
            <p className="mb-0 text-sm text-amber-900/80 dark:text-amber-100/80">
              本服务按&quot;原样&quot;和&quot;现有&quot;基础提供。秒构AI不对AI生成代码的安全性、可靠性、适用性作任何明示或暗示的保证。在将生成代码用于生产环境前，您有责任进行充分的测试和安全审查。对于因使用本服务导致的任何直接、间接、偶然或后果性损失（包括但不限于数据丢失、利润损失），我们在法律允许的范围内免责。
            </p>
          </div>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">5. 协议终止</h2>
          <p className="mb-8">
            如果您违反本条款，我们有权随时暂停或终止您的账户服务。您也可以随时申请注销账户，注销后您的数据将被依法清除或匿名化处理。
          </p>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">6. 法律适用</h2>
          <p className="mb-4">
            本条款受中华人民共和国法律管辖。如发生争议，双方应友好协商解决；协商不成的，应提交上海市浦东新区人民法院诉讼解决。
          </p>
        </div>
      </main>
      <Footer />
    </div>
  );
}