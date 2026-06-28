import { useEffect, useRef } from 'react';
import { MessageSquare, Loader2 } from 'lucide-react';
import { useChatStore } from '@store/useChatStore';
import { MessageBubble } from './MessageBabble';
import { ChatInput } from './ChatInput';
import styles from './Chat.module.scss';

export function Chat() {
  const {
    messages,
    isConnected,
    isTyping,
    isAuthenticated,
    isLoading,
    connect,
    disconnect,
    sendMessage,
    checkAuth,
  } = useChatStore();

  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  useEffect(() => {
    if (isAuthenticated) {
      connect();
      return () => disconnect();
    }
  }, [isAuthenticated, connect, disconnect]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  if (isLoading) {
    return (
      <div className={styles.container}>
        <div className={styles.loadingState}>
          <Loader2 className={styles.spinner} size={48} />
          <p>Проверяю, кто ты...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.messagesArea}>
        {messages.length === 0 ? (
          <div className={styles.emptyState}>
            <MessageSquare />
            <p>{isAuthenticated ? 'Напишите сообщение...' : 'Начните диалог с AI-ассистентом'}</p>
          </div>
        ) : (
          messages.map((msg) => (
            <MessageBubble key={msg.id} message={msg} />
          ))
        )}

        {isTyping && (
          <div className={styles.typingIndicator}>
            AI анализирует сообщение...
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <ChatInput
        onSend={sendMessage}
        disabled={!isAuthenticated ? false : !isConnected}
        placeholder={isAuthenticated ? 'Запиши расход или доход...' : 'Напиши своё имя...'}
      />
    </div>
  );
}