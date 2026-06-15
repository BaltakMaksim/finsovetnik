// src/components/Chat/Chat.tsx
import { useEffect, useRef } from 'react';
import { MessageSquare } from 'lucide-react';
import { useChatStore } from '@store/useChatStore';
import { MessageBubble } from './MessageBabble';
import { ChatInput } from './ChatInput';
import styles from './Chat.module.scss';

export function Chat() {
  const { messages, isConnected, isTyping, connect, disconnect, sendMessage } = useChatStore();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Подключаемся к WebSocket при монтировании
  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  // Автопрокрутка к последнему сообщению
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  return (
    <div className={styles.container}>
      <div className={styles.messagesArea}>
        {messages.length === 0 ? (
          <div className={styles.emptyState}>
            <MessageSquare />
            <p>Напишите сообщение, чтобы начать диалог с AI-ассистентом</p>
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

        {/* Невидимый якорь для автопрокрутки */}
        <div ref={messagesEndRef} />
      </div>

      <ChatInput
        onSend={sendMessage}
        disabled={!isConnected}
      />
    </div>
  );
}