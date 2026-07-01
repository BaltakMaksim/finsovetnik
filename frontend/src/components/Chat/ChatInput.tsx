import { useState } from 'react';
import { Send, Camera, Image as ImageIcon, Square, Mic } from 'lucide-react';
import styles from './ChatInput.module.scss';
import { audioService } from '@/services/audio';

interface ChatInputProps {
  onSend: (text: string,  isVoice?: boolean) => void;
  onScanReceipt: () => void;
  onUploadPhoto: () => void;
  disabled?: boolean;
}

export function ChatInput({ onSend, onScanReceipt, onUploadPhoto, disabled }: ChatInputProps) {
  const [text, setText] = useState('');
  const [isRecording, setIsRecording] = useState(false);  
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (text.trim()) {
      onSend(text.trim());
      setText('');
    }
  };
   const handleMicClick = async () => {
  if (isRecording) {
    // Останавливаем запись
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
        onSend(recognizedText, true); //  isVoice = true
      } else {
        console.warn('⚠️ Распознанный текст пуст');
      }
    } catch (error) {
      console.error('❌ Ошибка распознавания:', error);
      alert('Не удалось распознать речь. Попробуйте ещё раз.');
    }
  } else {
    //  Начинаем запись
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
        className={`${styles.micButton} ${isRecording ? styles.recording : ''}`}
        onClick={handleMicClick}
        disabled={disabled}
        title={isRecording ? 'Остановить запись' : 'Нажмите, чтобы говорить'}
      >
        {isRecording ? <Square size={18} /> : <Mic size={18} />}
      </button>

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