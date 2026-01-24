import { redirect } from 'next/navigation';

/**
 * 预览页兼容重定向。
 * 是什么：/preview/[id] 的兼容入口。
 * 做什么：统一跳转到生成结果页 /wizard/[id]。
 * 为什么：预览页移除后，下载入口收敛在向导完成页。
 */
export default function PreviewRedirect({ params }: { params: { id: string } }) {
  redirect(`/wizard/${params.id}`);
}
