import { useState } from 'react';
import { Send, Camera, Image as ImageIcon } from 'lucide-react';
import styles from './ChatInput.module.scss';

interface ChatInputProps {
  onSend: (text: string) => void;
  onScanReceipt: () => void;
  onUploadPhoto: () => void; // ✅ НОВОЕ
  disabled?: boolean;
}

export function ChatInput({ onSend, onScanReceipt, onUploadPhoto, disabled }: ChatInputProps) {
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
      {/* Кнопка QR-сканера */}
      <button
        type="button"
        className={styles.micButton}
        onClick={onScanReceipt}
        title="Сканировать QR-код чека"
        disabled={disabled}
      >
        <Camera size={20} />
      </button>

      {/* ✅ Кнопка загрузки фото */}
      <button
        type="button"
        className={styles.micButton}
        onClick={onUploadPhoto}
        title="Загрузить фото чека"
        disabled={disabled}
      >
        <ImageIcon size={20} />
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