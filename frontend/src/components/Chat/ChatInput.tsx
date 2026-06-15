// src/components/Chat/ChatInput.tsx
import { useState, useRef, useEffect, type KeyboardEvent } from 'react';
import { Send } from 'lucide-react';
import styles from './ChatInput.module.scss';

interface Props {
  onSend: (text: string) => void;
  disabled?: boolean;
}

export function ChatInput({ onSend, disabled = false }: Props) {
  const [text, setText] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Автофокус при монтировании
  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  // Автоматическая подстройка высоты textarea
  useEffect(() => {
    const el = textareaRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
    }
  }, [text]);

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;

    onSend(trimmed);
    setText('');

    // Сброс высоты после отправки
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter без Shift = отправка
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className={styles.wrapper}>
      <div className={styles.inputWrapper}>
        <textarea
          ref={textareaRef}
          className={styles.textarea}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Напишите сообщение..."
          disabled={disabled}
          rows={1}
        />
        <div className={styles.hint}>
          Enter — отправить, Shift+Enter — новая строка
        </div>
      </div>

      <button
        className={styles.sendButton}
        onClick={handleSend}
        disabled={!text.trim() || disabled}
        aria-label="Отправить сообщение"
      >
        <Send />
      </button>
    </div>
  );
}