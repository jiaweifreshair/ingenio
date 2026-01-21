"use client";

import { useLanguage } from "@/contexts/LanguageContext";
import { Button } from "@/components/ui/button";
import { Languages } from "lucide-react";

export function LanguageSwitcher() {
  const { language, setLanguage, t } = useLanguage();

  const toggleLanguage = () => {
    setLanguage(language === 'en' ? 'zh' : 'en');
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggleLanguage}
      title={language === 'en' ? t('ui.switch_to_chinese') : t('ui.switch_to_english')}
      className="text-blue-500 hover:text-blue-600"
    >
      <Languages className="h-5 w-5" />
      <span className="sr-only">{t('ui.toggle_language')}</span>
      <span className="ml-1 text-xs font-medium">{language.toUpperCase()}</span>
    </Button>
  );
}
