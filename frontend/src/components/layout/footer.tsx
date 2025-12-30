import Link from "next/link";

/**
 * Footer组件
 * Ingenio 妙构页脚
 */
export function Footer(): React.ReactElement {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="border-t border-border">
      <div className="container flex flex-col items-center justify-between gap-4 py-10 md:h-24 md:flex-row md:py-0">
        <div className="flex flex-col items-center gap-4 px-8 md:flex-row md:gap-2 md:px-0">
          <p className="text-center text-sm leading-loose text-muted-foreground md:text-left">
            © {currentYear} Ingenio 妙构. All rights reserved.
          </p>
        </div>
        <nav className="flex items-center space-x-4 text-sm text-muted-foreground">
          <Link href="/privacy" className="hover:text-primary">
            隐私政策
          </Link>
          <Link href="/terms" className="hover:text-primary">
            服务条款
          </Link>
          <Link href="/contact" className="hover:text-primary">
            联系我们
          </Link>
        </nav>
      </div>
    </footer>
  );
}
