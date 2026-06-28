import { useState, useRef, useEffect, type KeyboardEvent } from 'react';
import { Send, Mic, Square } from 'lucide-react';
import { audioService } from '@services/audio';
import styles from './ChatInput.module.scss';

interface Props {
  onSend: (text: string, isVoice?: boolean) => void;
  disabled?: boolean;
  placeholder?: string;
}

export function ChatInput({ onSend, disabled = false, placeholder = 'Напишите сообщение...' }: Props) {
  const [text, setText] = useState('');
  const [isRecording, setIsRecording] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

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

    onSend(trimmed, false); // Текстовое сообщение
    setText('');

    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

 const handleMicClick = async () => {
  if (isRecording) {
    // ⏹️ Останавливаем запись
    setIsRecording(false);
    try {
      const audioBlob = await audioService.stopRecording();
      
      // Отправляем аудио на распознавание
      const formData = new FormData();
      formData.append('file', audioBlob, 'recording.webm');

      const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

      const response = await fetch(`${API_URL}/api/transcribe`, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error('❌ Ошибка сервера:', errorText);
        throw new Error('Ошибка распознавания');
      }

      const data = await response.json();
      const recognizedText = data.text;

      // Отправляем распознанный текст в чат как голосовое сообщение
      if (recognizedText && recognizedText.trim()) {
        onSend(recognizedText, true); // ✅ isVoice = true
      } else {
        console.warn('⚠️ Распознанный текст пуст');
      }
    } catch (error) {
      console.error('❌ Ошибка распознавания:', error);
      alert('Не удалось распознать речь. Попробуйте ещё раз.');
    }
  } else {
    // 🎤 Начинаем запись
    try {
      await audioService.startRecording();
      setIsRecording(true);
    } catch (error) {
      console.error('❌ Ошибка доступа к микрофону:', error);
      alert('Не удалось получить доступ к микрофону. Проверьте разрешения браузера.');
    }
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
          placeholder={placeholder}
          disabled={disabled || isRecording}
          rows={1}
        />
        <div className={styles.hint}>
          Enter — отправить, Shift+Enter — новая строка
        </div>
      </div>

      {/* ✅ Кнопка микрофона */}
      <button
        className={`${styles.micButton} ${isRecording ? styles.recording : ''}`}
        onClick={handleMicClick}
        disabled={disabled}
        title={isRecording ? 'Остановить запись' : 'Нажмите, чтобы говорить'}
      >
        {isRecording ? <Square size={18} /> : <Mic size={18} />}
      </button>

      <button
        className={styles.sendButton}
        onClick={handleSend}
        disabled={!text.trim() || disabled || isRecording}
        aria-label="Отправить сообщение"
      >
        <Send />
      </button>
    </div>
  );
}