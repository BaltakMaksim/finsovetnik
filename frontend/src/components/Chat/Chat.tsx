import { useEffect, useRef, useState } from 'react';
import { MessageSquare, Loader2 } from 'lucide-react';
import { useChatStore } from '@store/useChatStore';
import { MessageBubble } from './MessageBabble';
import { ChatInput } from './ChatInput';
import { QRScanner } from '@components/Reciept/QRScanner';
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

  const [isScanning, setIsScanning] = useState(false);
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

  //  Обработчик успешного сканирования QR
  const handleScanSuccess = async (qrData: string) => {
    setIsScanning(false);

    try {
      const response = await fetch('/api/receipts/scan', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('access_token')}`,
        },
        body: JSON.stringify({ qr_data: qrData }),
      });

      const data = await response.json();

      if (data.success) {
        //  Добавляем сообщение от AI в чат
        const aiMessage = {
          id: crypto.randomUUID(),
          text: ` Чек распознан!\n Сумма: ${data.data.amount}₽\n Дата: ${data.data.date}`,
          sender: 'ai' as const,
          timestamp: Date.now(),
          is_financial: true,
          transactions: [
            {
              amount: data.data.amount,
              category: 'Покупка (QR)',
              owner: 'user',
              type: 'EXPENSE',
            },
          ],
        };

        useChatStore.setState((state:any) => ({
          messages: [...state.messages, aiMessage],
        }));
      } else {
        //  Ошибка от сервера
        const errorMessage = {
          id: crypto.randomUUID(),
          text: `❌ Ошибка: ${data.error || 'Не удалось распознать чек'}`,
          sender: 'ai' as const,
          timestamp: Date.now(),
        };

        useChatStore.setState((state) => ({
          messages: [...state.messages, errorMessage],
        }));
      }
    } catch (error) {
      console.error('Ошибка сканирования:', error);
      
      const errorMessage = {
        id: crypto.randomUUID(),
        text: ' Не удалось отправить чек на сервер. Проверь подключение.',
        sender: 'ai' as const,
        timestamp: Date.now(),
      };

      useChatStore.setState((state) => ({
        messages: [...state.messages, errorMessage],
      }));
    }
  };

  //  Обработчик закрытия сканера
  const handleScanClose = () => {
    setIsScanning(false);
  };

  // Обработчик ошибки сканера
  const handleScanError = (error: string) => {
    console.error('QR Scanner error:', error);
    setIsScanning(false);

    const errorMessage = {
      id: crypto.randomUUID(),
      text: ` ${error}`,
      sender: 'ai' as const,
      timestamp: Date.now(),
    };

    useChatStore.setState((state) => ({
      messages: [...state.messages, errorMessage],
    }));
  };

  //  Обработчик открытия сканера
  const handleOpenScanner = () => {
    if (!isAuthenticated) {
      const errorMessage = {
        id: crypto.randomUUID(),
        text: ' Сначала нужно авторизоваться, чтобы сканировать чеки.',
        sender: 'ai' as const,
        timestamp: Date.now(),
      };

      useChatStore.setState((state) => ({
        messages: [...state.messages, errorMessage],
      }));
      return;
    }

    setIsScanning(true);
  };

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
            <p>
              {isAuthenticated
                ? 'Напишите сообщение или отсканируйте чек 📱'
                : 'Начните диалог с AI-ассистентом'}
            </p>
          </div>
        ) : (
          messages.map((msg) => (
            <MessageBubble key={msg.id} message={msg} />
          ))
        )}

        {isTyping && (
          <div className={styles.typingIndicator}>AI анализирует сообщение...</div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <ChatInput
        onSend={sendMessage}
        onScanReceipt={handleOpenScanner}
        disabled={!isAuthenticated ? false : !isConnected}
      />

      {/* ✅ Модальное окно QR-сканера */}
      {isScanning && (
        <QRScanner
          onSuccess={handleScanSuccess}
          onClose={handleScanClose}
          onError={handleScanError}
        />
      )}
    </div>
  );
}