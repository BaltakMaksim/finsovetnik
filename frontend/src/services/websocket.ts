// src/services/websocket.ts
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = 'http://localhost:8080/ws';

class WebSocketService {
  private client: Client | null = null;
  private onMessageCallback: ((message: Record<string, unknown>) => void) | null = null;
  private onStatusChange: ((connected: boolean) => void) | null = null;

  /**
   * Подключается к WebSocket серверу
   */
  connect(onMessage: (msg: Record<string, unknown>) => void, onStatus?: (connected: boolean) => void): void {
    this.onMessageCallback = onMessage;
    this.onStatusChange = onStatus ?? null;

    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000, // Автопереподключение через 5 сек
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        // В продакшене можно убрать
        if (import.meta.env.DEV) {
          console.log('[STOMP]', str);
        }
      },
    });

    this.client.onConnect = () => {
      console.log('✅ WebSocket подключен');
      this.onStatusChange?.(true);

      // Подписываемся на ответы от AI
      this.client?.subscribe('/topic/chat.responses', (message: IMessage) => {
        try {
          const body = JSON.parse(message.body);
          this.onMessageCallback?.(body);
        } catch (e) {
          console.error('❌ Ошибка парсинга сообщения:', e);
        }
      });
    };

    this.client.onDisconnect = () => {
      console.log('🔌 WebSocket отключен');
      this.onStatusChange?.(false);
    };

    this.client.onStompError = (frame) => {
      console.error('❌ STOMP ошибка:', frame.headers['message']);
      this.onStatusChange?.(false);
    };

    this.client.activate();
  }

  /**
   * Отправляет сообщение в чат
   */
  sendMessage(text: string): void {
    if (!this.client?.connected) {
      console.warn('⚠️ WebSocket не подключен');
      return;
    }

    this.client.publish({
      destination: '/app/chat.message',
      body: JSON.stringify({ text }),
    });
  }

  /**
   * Отключается от сервера
   */
  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.onMessageCallback = null;
    this.onStatusChange = null;
  }

  /**
   * Проверяет статус подключения
   */
  get isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

// Экспортируем синглтон
export const wsService = new WebSocketService();