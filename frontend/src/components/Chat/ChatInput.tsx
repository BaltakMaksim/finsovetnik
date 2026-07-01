import { useState, useRef, useEffect } from 'react';
import { Send, Camera, Image as ImageIcon, Square, Mic, Plus, X } from 'lucide-react';
import styles from './ChatInput.module.scss';
import { audioService } from '@/services/audio';

interface ChatInputProps {
  onSend: (text: string, isVoice?: boolean) => void;
  onScanReceipt: () => void;
  onUploadPhoto: () => void;
  disabled?: boolean;
}

export function ChatInput({ onSend, onScanReceipt, onUploadPhoto, disabled }: ChatInputProps) {
  const [text, setText] = useState('');
  const [isRecording, setIsRecording] = useState(false);
  const [showActions, setShowActions] = useState(false); // ✅ Раскрывающееся меню
  const actionsRef = useRef<HTMLDivElement>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (text.trim()) {
      onSend(text.trim());
      setText('');
    }
  };

  const handleMicClick = async () => {
    if (isRecording) {
      setIsRecording(false);
      setShowActions(false); // ✅ Закрываем меню после действия
      try {
        const audioBlob = await audioService.stopRecording();
        const formData = new FormData();
        formData.append('file', audioBlob, 'recording.webm');

        const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
        const response = await fetch(`${API_URL}/api/transcribe`, {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) throw new Error('Ошибка распознавания');

        const data = await response.json();
        if (data.text && data.text.trim()) {
          onSend(data.text, true);
        }
      } catch (error) {
        console.error('❌ Ошибка распознавания:', error);
        alert('Не удалось распознать речь. Попробуйте ещё раз.');
      }
    } else {
      try {
        await audioService.startRecording();
        setIsRecording(true);
        setShowActions(false); // ✅ Закрываем меню
      } catch (error) {
        console.error('❌ Ошибка доступа к микрофону:', error);
        alert('Не удалось получить доступ к микрофону.');
      }
    }
  };

  // ✅ Закрытие меню при клике вне его
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (actionsRef.current && !actionsRef.current.contains(e.target as Node)) {
        setShowActions(false);
      }
    };

    if (showActions) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showActions]);

  // ✅ Обработчики действий с закрытием меню
  const handleScanClick = () => {
    setShowActions(false);
    onScanReceipt();
  };

  const handleUploadClick = () => {
    setShowActions(false);
    onUploadPhoto();
  };

  return (
    <form className={styles.wrapper} onSubmit={handleSubmit}>
      {/* ✅ Кнопка "+" — раскрывает меню на мобильном */}
      <button
        type="button"
        className={`${styles.toggleButton} ${showActions ? styles.active : ''}`}
        onClick={() => setShowActions(!showActions)}
        disabled={disabled}
        title="Дополнительные действия"
      >
        {showActions ? <X size={20} /> : <Plus size={20} />}
      </button>

      {/* ✅ Панель действий (раскрывается на мобильном) */}
      <div 
        ref={actionsRef}
        className={`${styles.actionPanel} ${showActions ? styles.show : ''}`}
      >
        <button
          type="button"
          className={styles.actionButton}
          onClick={handleScanClick}
          disabled={disabled}
          title="Сканировать QR-код"
        >
          <Camera size={20} />
          <span className={styles.actionLabel}>QR</span>
        </button>

        <button
          type="button"
          className={styles.actionButton}
          onClick={handleUploadClick}
          disabled={disabled}
          title="Загрузить фото чека"
        >
          <ImageIcon size={20} />
          <span className={styles.actionLabel}>Фото</span>
        </button>

        <button
          type="button"
          className={`${styles.actionButton} ${isRecording ? styles.recording : ''}`}
          onClick={handleMicClick}
          disabled={disabled}
          title={isRecording ? 'Остановить запись' : 'Голосовой ввод'}
        >
          {isRecording ? <Square size={20} /> : <Mic size={20} />}
          <span className={styles.actionLabel}>
            {isRecording ? 'Стоп' : 'Голос'}
          </span>
        </button>
      </div>

      {/* ✅ Поле ввода — занимает максимум места */}
      <div className={styles.inputWrapper}>
        <textarea
          className={styles.textarea}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Напиши сообщение..."
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

      {/*  Кнопка отправки */}
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