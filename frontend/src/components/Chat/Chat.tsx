import { useEffect, useRef, useState } from 'react';
import { MessageSquare, Loader2 } from 'lucide-react';
import { useChatStore } from '@store/useChatStore';
import { MessageBubble } from './MessageBabble';
import { ChatInput } from './ChatInput';
import { QRScanner } from '@components/Reciept/QRScanner';
import { PhotoUploader } from '@components/Reciept/PhotoUploader';
import styles from './Chat.module.scss';
import { API_URL } from '@/services/auth';

export function Chat() {
  const {
    messages,
    isConnected,
    isTyping,
    isAuthenticated,
    isLoading,
    lastReceiptId,
    connect,
    disconnect,
    sendMessage,
    checkAuth,
    loadHistory,
    setLastReceiptId,
  } = useChatStore();

  const [isScanning, setIsScanning] = useState(false);
  const [isUploadingPhoto, setIsUploadingPhoto] = useState(false);
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
    if (isConnected && isAuthenticated) {
      console.log('✅ WebSocket подключен, загружаем историю...');
      loadHistory();
    }
  }, [isConnected, isAuthenticated, loadHistory]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  // =========================================================================
  // ОБРАБОТЧИКИ QR-СКАНЕРА
  // =========================================================================
  const handleScanSuccess = async (qrData: string) => {
    setIsScanning(false);

    try {
      const response = await fetch(`${API_URL}/api/receipts/scan`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('access_token')}`,
        },
        body: JSON.stringify({ qr_data: qrData }),
      });

      const data = await response.json();

      if (data.success) {
        // ✅ Сохраняем receipt_id для последующей загрузки фото
        setLastReceiptId(data.data.receipt_id);

        const aiMessage = {
          id: crypto.randomUUID(),
          text: `✅ QR-код распознан!\n💰 Сумма: ${data.data.amount}₽\n📅 Дата: ${data.data.date}\n\n📸 Загрузи фото чека, чтобы узнать, на что именно потрачены деньги.`,
          sender: 'ai' as const,
          timestamp: Date.now(),
          is_financial: true,
          transactions: [
            {
              amount: data.data.amount,
              category: 'Чек (ожидает детализации)',
              owner: 'user',
              type: 'EXPENSE',
            },
          ],
        };

        useChatStore.setState((state: any) => ({
          messages: [...state.messages, aiMessage],
        }));

        // ✅ Автоматически открываем загрузку фото через 2 секунды
        setTimeout(() => {
          setIsUploadingPhoto(true);
        }, 2000);
      } else {
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
        text: '❌ Не удалось отправить чек на сервер. Проверь подключение.',
        sender: 'ai' as const,
        timestamp: Date.now(),
      };

      useChatStore.setState((state) => ({
        messages: [...state.messages, errorMessage],
      }));
    }
  };

  const handleScanClose = () => {
    setIsScanning(false);
  };

  const handleScanError = (error: string) => {
    console.error('QR Scanner error:', error);
    setIsScanning(false);

    const errorMessage = {
      id: crypto.randomUUID(),
      text: `❌ ${error}`,
      sender: 'ai' as const,
      timestamp: Date.now(),
    };

    useChatStore.setState((state) => ({
      messages: [...state.messages, errorMessage],
    }));
  };

  const handleOpenScanner = () => {
    if (!isAuthenticated) {
      const errorMessage = {
        id: crypto.randomUUID(),
        text: '⚠️ Сначала нужно авторизоваться, чтобы сканировать чеки.',
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

  // =========================================================================
  // ОБРАБОТЧИКИ ЗАГРУЗКИ ФОТО
  // =========================================================================
  const handleOpenPhotoUploader = () => {
    if (!isAuthenticated) {
      const errorMessage = {
        id: crypto.randomUUID(),
        text: '⚠️ Сначала нужно авторизоваться, чтобы загружать чеки.',
        sender: 'ai' as const,
        timestamp: Date.now(),
      };

      useChatStore.setState((state) => ({
        messages: [...state.messages, errorMessage],
      }));
      return;
    }

    setIsUploadingPhoto(true);
  };

  const handlePhotoSuccess = (data: any) => {
    setIsUploadingPhoto(false);
    setLastReceiptId(null); // ✅ Сбрасываем ID после успешного слияния

    const items = data.items || [];
    const total = data.total_amount || 0;
    const store = data.store_name || 'магазина';

    // Формируем красивое сообщение с деталями
    let details = `📸 Чек из ${store} распознан!\n💰 Всего: ${total}₽\n🛍️ Товаров: ${items.length}\n\nДетали:\n`;
    items.forEach((item: any) => {
      details += `• ${item.name}: ${item.sum}₽ (${item.category})\n`;
    });

    const aiMessage = {
      id: crypto.randomUUID(),
      text: details,
      sender: 'ai' as const,
      timestamp: Date.now(),
      is_financial: true,
    };

    useChatStore.setState((state: any) => ({
      messages: [...state.messages, aiMessage],
    }));
  };

  const handlePhotoClose = () => {
    setIsUploadingPhoto(false);
    // Не сбрасываем receipt_id — пользователь может передумать и загрузить позже
  };

  const handlePhotoError = (error: string) => {
    console.error('Photo upload error:', error);
    setIsUploadingPhoto(false);

    const errorMessage = {
      id: crypto.randomUUID(),
      text: `❌ Ошибка загрузки фото: ${error}`,
      sender: 'ai' as const,
      timestamp: Date.now(),
    };

    useChatStore.setState((state) => ({
      messages: [...state.messages, errorMessage],
    }));
  };

  // =========================================================================
  // РЕНДЕР
  // =========================================================================
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
        onUploadPhoto={handleOpenPhotoUploader}
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

      {/* ✅ Модальное окно загрузки фото */}
      {isUploadingPhoto && (
        <PhotoUploader
          receiptId={lastReceiptId || undefined}
          onSuccess={handlePhotoSuccess}
          onClose={handlePhotoClose}
          onError={handlePhotoError}
        />
      )}
    </div>
  );
}