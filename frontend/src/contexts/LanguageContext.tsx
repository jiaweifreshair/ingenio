"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { LocaleType, translations, type Translations } from '../i18n/locales';

interface LanguageContextProps {
  language: LocaleType;
  setLanguage: (lang: LocaleType) => void;
  t: (key: string) => string;
}

const LanguageContext = createContext<LanguageContextProps | undefined>(undefined);

export const LanguageProvider = ({ children }: { children: ReactNode }) => {
  // 默认使用中文
  const [language, setLanguageState] = useState<LocaleType>('zh');

  useEffect(() => {
    // Load persisted language preference
    const savedLang = localStorage.getItem('app_language') as LocaleType;
    if (savedLang && (savedLang === 'en' || savedLang === 'zh')) {
      setLanguageState(savedLang);
    }
  }, []);

  const setLanguage = (lang: LocaleType) => {
    setLanguageState(lang);
    localStorage.setItem('app_language', lang);
  };

  // Nested object property accessor
  // e.g. t('hero.title') -> translations[lang]['hero']['title']
  const t = (path: string): string => {
    const keys = path.split('.');
    let current: Translations | string = translations[language];

    for (const key of keys) {
      if (typeof current === 'string') {
        console.warn(`Translation key refers to a string, not an object: ${path}`);
        return path;
      }

      const nextValue = (current as Translations)[key] as Translations[string] | undefined;
      if (nextValue === undefined) {
        console.warn(`Missing translation for key: ${path} in language: ${language}`);
        return path; // Fallback to key
      }

      current = nextValue;
    }

    if (typeof current !== 'string') {
        console.warn(`Translation key refers to an object, not a string: ${path}`);
        return path;
    }

    return current;
  };

  // Prevent flash of wrong language content if needed, 
  // though for client-side rendering initial state 'en' is fine.
  // If we want to strictly wait for localStorage check, we can return null until isLoaded.
  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (context === undefined) {
    throw new Error('useLanguage must be used within a LanguageProvider');
  }
  return context;
};
