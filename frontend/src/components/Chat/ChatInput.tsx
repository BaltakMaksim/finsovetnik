import { useState } from 'react';
import { Send, Camera } from 'lucide-react';
import styles from './ChatInput.module.scss';

interface ChatInputProps {
  onSend: (text: string) => void;
  onScanReceipt: () => void; // Теперь открывает сканер
  disabled?: boolean;
}

export function ChatInput({ onSend, onScanReceipt, disabled }: ChatInputProps) {
  const [text, setText] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (text.trim()) {
      onSend(text.trim());
      setText('');
    }
  };

  return (
    <form className={styles.wrapper} onSubmit={handleSubmit}>
      {/* ✅ Кнопка сканирования QR */}
      <button
        type="button"
        className={styles.micButton}
        onClick={onScanReceipt}
        title="Сканировать чек"
      >
        <Camera size={20} />
      </button>

      <div className={styles.inputWrapper}>
        <textarea
          className={styles.textarea}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Напиши сообщение или отсканируй чек..."
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSubmit(e);
            }
          }}
          disabled={disabled}
          rows={1}
        />
      </div>

      <button 
        type="submit" 
        className={styles.sendButton} 
        disabled={!text.trim() || disabled}
      >
        <Send />
      </button>
    </form>
  );
}