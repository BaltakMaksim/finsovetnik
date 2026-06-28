import { useState } from 'react';
import { Volume2, Pause } from 'lucide-react';
import { ttsService } from '@services/tts';
import styles from './MessageBabble.module.scss';
import clsx from 'clsx';
import dayjs from 'dayjs';
import { TypeTransaction, type Message } from '@store/useChatStore';
import { SeedDisplay } from './SeedDisplay';

interface Props {
  message: Message;
}

export function MessageBubble({ message }: Props) {
  const isUser = message.sender === 'user';
  const [isPlaying, setIsPlaying] = useState(false);

  const handlePlay = async () => {
    // Если уже играет — останавливаем
    if (isPlaying) {
      ttsService.stopAudio();
      setIsPlaying(false);
      return;
    }

    setIsPlaying(true);
    try {
      // 1. Превращаем текст в аудио
      const audioUrl = await ttsService.textToSpeech(message.text);
      // 2. Проигрываем
      await ttsService.playAudio(audioUrl);
    } catch (error) {
      console.error('❌ Ошибка озвучки:', error);
    } finally {
      // 3. Возвращаем кнопку в исходное состояние
      setIsPlaying(false);
    }
  };

  return (
    <div className={clsx(styles.wrapper, isUser ? styles.user : styles.ai)}>
      <div>
        <div className={styles.bubble}>
          
          {/* ✅ Кнопка "Слушать" только для сообщений от AI */}
          {!isUser && message.text && (
            <button 
              className={clsx(styles.listenBtn, isPlaying && styles.playing)}
              onClick={handlePlay}
            >
              {isPlaying ? <Pause size={14} /> : <Volume2 size={14} />}
              <span>{isPlaying ? 'Воспроизводится...' : 'Слушать'}</span>
            </button>
          )}

          <p>{message.text}</p>
          
          {message.seedWords && <SeedDisplay words={message.seedWords} />}
          
          {message.transactions && message.transactions.length > 0 && (
            <div className={styles.transactions}>
              {message.transactions.map((t, i) => (
                <span 
                  key={i} 
                  className={t.type === TypeTransaction.INCOME ? styles.transactionTag : styles.transactionTagExp}
                >
                  {t.type === TypeTransaction.INCOME ? "+" : "-"}{t.amount}₽ · {t.category}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className={styles.timestamp}>
          {dayjs(message.timestamp).format('HH:mm')}
        </div>
      </div>
    </div>
  );
}