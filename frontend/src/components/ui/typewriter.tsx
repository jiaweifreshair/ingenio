'use client';

import { useState, useEffect } from 'react';

interface TypewriterProps {
  text: string;
  speed?: number;
  className?: string;
  cursor?: boolean;
  instant?: boolean;
}

export function Typewriter({ text, speed = 15, className, cursor = true, instant = false }: TypewriterProps) {
  const [displayedText, setDisplayedText] = useState(instant ? text : '');

  useEffect(() => {
    if (instant) {
        if (displayedText !== text) setDisplayedText(text);
        return;
    }

    if (!text) {
        setDisplayedText('');
        return;
    }

    // 如果目标文本与当前显示文本不匹配（不是追加关系），说明是新内容，重置
    if (!text.startsWith(displayedText)) {
        setDisplayedText('');
        // return; // Let the timeout handle the first char
    }

    // 如果已经显示完了，不需要做任何事
    if (displayedText.length === text.length) {
        return;
    }

    // 动态调整速度：如果落后太多，加速
    const lag = text.length - displayedText.length;
    const dynamicSpeed = lag > 10 ? speed / 2 : speed;

    // 继续打字
    const timeout = setTimeout(() => {
      setDisplayedText(text.slice(0, displayedText.length + 1));
    }, dynamicSpeed);

    return () => clearTimeout(timeout);
  }, [text, displayedText, speed, instant]);

  return (
    <span className={className}>
      {displayedText}
      {cursor && !instant && displayedText.length < text.length && (
        <span className="inline-block w-1.5 h-4 align-middle bg-blue-400 animate-pulse ml-0.5" />
      )}
    </span>
  );
}
