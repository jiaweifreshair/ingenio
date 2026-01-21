"use client";

import Link from "next/link";
import { useLanguage } from "@/contexts/LanguageContext";

/**
 * Footer组件
 * Ingenio 妙构页脚
 */
export function Footer(): React.ReactElement {
  const currentYear = new Date().getFullYear();
  const { t } = useLanguage();

  return (
    <footer className="border-t border-border">
      <div className="container flex flex-col items-center justify-between gap-4 py-10 md:h-24 md:flex-row md:py-0">
        <div className="flex flex-col items-center gap-4 px-8 md:flex-row md:gap-2 md:px-0">
          <p className="text-center text-sm leading-loose text-muted-foreground md:text-left">
            {t('footer.rights').replace('2026', currentYear.toString())}
          </p>
        </div>
        <nav className="flex items-center space-x-4 text-sm text-muted-foreground">
          <Link href="/privacy" className="hover:text-primary">
            {t('footer.privacy')}
          </Link>
          <Link href="/terms" className="hover:text-primary">
            {t('footer.terms')}
          </Link>
          <Link href="/contact" className="hover:text-primary">
            {t('footer.contact')}
          </Link>
        </nav>
      </div>
    </footer>
  );
}
