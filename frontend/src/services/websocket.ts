import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { authService } from '@services/auth';

const WS_URL = `${window.location.protocol}//${window.location.host}/ws`;
//const WS_URL = `http://localhost:8080/ws`;

class WebSocketService {
  private client: Client | null = null;
  private onMessageCallback: ((message: Record<string, unknown>) => void) | null = null;
  private onStatusChange: ((connected: boolean) => void) | null = null;

  async connect(onMessage: (msg: Record<string, unknown>) => void, onStatus?: (connected: boolean) => void): Promise<void> {
    this.onMessageCallback = onMessage;
    this.onStatusChange = onStatus ?? null;

    
    const accessToken = authService.getAccessToken();
    
    if (!accessToken) {
      console.error('❌ Нет access token для WebSocket');
      return;
    }

    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`
      },
      
      debug: (str) => {
        if (import.meta.env.DEV) {
          console.log('[STOMP]', str);
        }
      },
    });

    this.client.onConnect = () => {
      console.log('✅ WebSocket подключен');
      this.onStatusChange?.(true);

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

    this.client.onStompError = async (frame) => {
      console.error('❌ STOMP ошибка:', frame.headers['message']);
      this.onStatusChange?.(false);

      if (frame.headers['message']?.includes('Unauthorized') || frame.headers['message']?.includes('401')) {
        console.log('🔄 Токен невалиден, пытаемся обновить...');
        
        const data = await authService.tryAutoLogin()
        
        if (data?.accessToken) {
          this.client?.deactivate();
          setTimeout(() => {
            this.connect(this.onMessageCallback!, this.onStatusChange!);
          }, 1000);
        } else {
          authService.logout();
        }
      }
    };

    this.client.activate();
  }

  sendMessage(text: string): void {
    if (!this.client?.connected) {
      console.warn('⚠️ WebSocket не подключен');
      return;
    }

    // ✅ Отправляем сообщение БЕЗ user_id (сервер возьмёт из токена)
    this.client.publish({
      destination: '/app/chat.message',
      body: JSON.stringify({ text }),
    });
  }

  disconnect(): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }
    this.onMessageCallback = null;
    this.onStatusChange = null;
  }

  get isConnected(): boolean {
    return this.client?.connected ?? false;
  }
}

export const wsService = new WebSocketService();