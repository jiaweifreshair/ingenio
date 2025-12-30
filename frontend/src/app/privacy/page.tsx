import { TopNav } from "@/components/layout/top-nav";
import { Footer } from "@/components/layout/footer";

export default function PrivacyPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <TopNav />
      <main className="flex-1 container mx-auto px-4 py-12 max-w-4xl">
        <h1 className="text-3xl md:text-4xl font-bold mb-8 text-foreground">隐私政策</h1>
        <div className="prose dark:prose-invert max-w-none text-muted-foreground">
          <p className="mb-8 text-lg leading-relaxed">
            最后更新日期：{new Date().toLocaleDateString()}
          </p>
          <div className="p-6 bg-slate-50 dark:bg-slate-900 rounded-2xl mb-12">
            <p className="mb-0">
              欢迎使用Ingenio 妙构（Ingenio）。我们深知个人信息对您的重要性，并承诺采取严格的安全保护措施，依法保护您的隐私信息。本《隐私政策》将详细说明我们在您使用服务时如何收集、使用、存储、共享和保护您的个人信息。
            </p>
          </div>
          
          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">1. 我们如何收集您的信息</h2>
          <p className="mb-4">
            为了向您提供高效的 AI 应用生成服务，我们会在以下场景中收集您的信息：
          </p>
          <ul className="list-disc pl-6 mb-8 space-y-3">
            <li><strong>账户注册与认证：</strong> 当您注册Ingenio 妙构账户时，我们需要收集您的手机号码、电子邮箱或第三方社交账号信息，以便为您创建唯一的用户身份。</li>
            <li><strong>AI 生成交互数据：</strong> 为了提供并优化 AI 生成服务，我们会收集您输入的自然语言描述、上传的图片参考、选择的行业模板以及您对生成结果的反馈。这些数据用于训练和调优我们的特定领域模型（除非您明确选择退出）。</li>
            <li><strong>应用构建数据：</strong> 您在使用平台构建应用时产生的代码、配置、数据库结构等技术资产。</li>
            <li><strong>日志与设备信息：</strong> 我们会自动收集您的 IP 地址、浏览器类型、访问时间以及操作日志，用于安全审计和故障排查。</li>
          </ul>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">2. 我们如何使用您的信息</h2>
          <p className="mb-4">
            收集的信息将仅用于以下合法目的：
          </p>
          <ul className="list-disc pl-6 mb-8 space-y-3">
            <li><strong>核心服务交付：</strong> 驱动 AI 引擎理解您的需求并生成相应的代码和界面。</li>
            <li><strong>产品优化：</strong> 分析用户行为模式，改进我们的算法准确性和生成速度。</li>
            <li><strong>安全保障：</strong> 监测异常登录、防范恶意攻击和滥用行为。</li>
            <li><strong>个性化推荐：</strong> 根据您的历史偏好，为您推荐更匹配的行业模板和设计风格。</li>
          </ul>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">3. AI 数据隐私与代码归属</h2>
          <p className="mb-4">
            针对 AI 生成平台的用户特别关注点，我们郑重承诺：
          </p>
          <ul className="list-disc pl-6 mb-8 space-y-3">
            <li><strong>代码所有权：</strong> 您使用Ingenio 妙构生成的应用程序代码、设计稿及相关资产的知识产权完全归您所有。我们不会对您生成的最终产品主张版权。</li>
            <li><strong>数据隔离：</strong> 企业版用户的数据将存储在独立的逻辑空间中，不会用于训练公共模型，确保企业核心机密不外泄。</li>
            <li><strong>敏感信息过滤：</strong> 我们的系统集成了敏感信息识别机制，会自动过滤并提示您避免上传身份证号、银行卡号等高度敏感的个人隐私信息。</li>
          </ul>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">4. 信息共享与披露</h2>
          <p className="mb-8">
            除法律法规规定或您明确同意外，我们不会向任何第三方出售或提供您的个人信息。为了实现短信发送、云服务托管等功能，我们需要与经过安全评估的合作伙伴（如阿里云、腾讯云）共享必要的脱敏信息。
          </p>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">5. 您的权利</h2>
          <p className="mb-8">
            您拥有查阅、更正、删除您个人信息以及注销账户的权利。您可以通过平台设置页面自行操作，或通过联系客服进行处理。对于 AI 生成的历史记录，您可以选择永久删除，删除后将无法恢复。
          </p>

          <h2 className="text-2xl font-semibold mt-12 mb-6 text-foreground">6. 联系我们</h2>
          <p className="mb-4">
            如果您对本隐私政策有任何疑问或投诉，请通过以下方式联系我们的隐私保护专员：
          </p>
          <div className="bg-slate-100 dark:bg-slate-800 p-4 rounded-lg inline-block">
            <p className="font-mono text-sm mb-0">Email: privacy@ingenio.ai</p>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}