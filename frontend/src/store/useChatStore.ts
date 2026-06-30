import { create } from 'zustand';
import { wsService } from '@services/websocket';
import { API_URL, authService } from '@services/auth';

export enum TypeTransaction {
  INCOME = "INCOME",
  EXPENSE = "EXPENSE",
}

export interface Transaction {
  amount: number;
  category: string;
  owner: string;
  type: TypeTransaction;
}

export interface Message {
  id: string;
  text: string;
  sender: 'user' | 'ai';
  is_financial?: boolean;
  timestamp: number;
  transactions?: Transaction[];
  seedWords?: string[];
}

interface ChatState {
  messages: Message[];
  isConnected: boolean;
  isTyping: boolean;
  isAuthenticated: boolean;
  username: string | null;
  userId: number | null;
  isLoading: boolean;
  lastReceiptId: string | null; 
  setLastReceiptId: (id: string | null) => void; 

  connect: () => void;
  disconnect: () => void;
  sendMessage: (text: string) => void;
  clearHistory: () => void;
  checkAuth: () => void;
  logout: () => void;
  loadHistory: () => Promise<void>; //  НОВЫЙ МЕТОД
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  isConnected: false,
  isTyping: false,
  isAuthenticated: false,
  username: null,
  userId: null,
  isLoading: true,
  lastReceiptId: null,

  checkAuth: async () => {
    set({ isLoading: true });

    const autoLoginResult = await authService.tryAutoLogin();

    if (autoLoginResult) {
      set({
        isLoading: false,
        isAuthenticated: true,
        username: autoLoginResult.username,
        userId: autoLoginResult.userId,
        messages: [{
          id: crypto.randomUUID(),
          text: `Привет, ${autoLoginResult.username}! 👋 Рад тебя снова видеть.`,
          sender: 'ai',
          timestamp: Date.now()
        }]
      });
    } else {
      set({
        isLoading: false,
        isAuthenticated: false,
        username: null,
        userId: null,
        messages: [{
          id: crypto.randomUUID(),
          text: 'Привет! Я твой финансовый помощник. Как тебя зовут?',
          sender: 'ai',
          timestamp: Date.now()
        }]
      });
    }
  },

  setLastReceiptId: (id: string | null) => {
    set({ lastReceiptId: id });
  },
  loadHistory: async () => {
    const { isAuthenticated, userId } = get();
    
    if (!isAuthenticated || !userId) {
      console.warn('Не удалось загрузить историю: пользователь не авторизован');
      return;
    }

    try {
      const response = await fetch(`${API_URL}/api/history`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('access_token')}`
        }
      });

      if (response.ok) {
        const data = await response.json();
        
        if (data.messages && data.messages.length > 0) {
          // Преобразуем в формат Message
          const historyMessages: Message[] = data.messages.map((msg: any) => ({
            id: msg.id,
            text: msg.text,
            sender: msg.sender as 'user' | 'ai',
            timestamp: msg.timestamp,
          }));

          set({ messages: historyMessages });
          console.log(`✅ Загружено ${historyMessages.length} сообщений из истории`);
        } else {
          console.log('История сообщений пуста');
        }
      } else {
        console.error('Ошибка загрузки истории:', response.status);
      }
    } catch (error) {
      console.error('Ошибка загрузки истории:', error);
    }
  },

  connect: () => {
    wsService.connect(
      (response: Record<string, unknown>) => {
        const aiMessage: Message = {
          id: crypto.randomUUID(),
          text: (response.text as string) || 'Нет ответа',
          sender: 'ai',
          is_financial: response.is_financial as boolean,
          timestamp: Date.now(),
          transactions: response.transactions as Transaction[] | undefined,
        };

        set((state) => ({
          messages: [...state.messages, aiMessage],
          isTyping: false,
        }));
      },
      (connected: boolean) => {
        set({ isConnected: connected });
      }
    );
  },

  disconnect: () => {
    wsService.disconnect();
    set({ isConnected: false });
  },

  sendMessage: async (text: string) => {
    if (!text.trim()) return;

    const userMessage: Message = {
      id: crypto.randomUUID(),
      text: text.trim(),
      sender: 'user',
      timestamp: Date.now(),
    };

    set((state) => ({
      messages: [...state.messages, userMessage],
      isTyping: true,
    }));

    const state = get();

    if (!state.isAuthenticated) {
      // Режим регистрации — через HTTP
      try {
        const response = await authService.sendMessage(text);

        const aiMessage: Message = {
          id: crypto.randomUUID(),
          text: response.reply,
          sender: 'ai',
          timestamp: Date.now(),
          seedWords: response.seed_words,
        };

        set((s) => ({
          messages: [...s.messages, aiMessage],
          isTyping: false,
        }));

        // Если аутентификация успешна — сохраняем всё, включая токены
        if (response.authenticated && response.username && response.user_id && response.access_token && response.refresh_token) {
          authService.saveAuth(
            response.username,
            response.user_id,
            response.access_token,
            response.refresh_token
          );
          set({
            isAuthenticated: true,
            username: response.username,
            userId: response.user_id,
          });
        }
      } catch (error) {
        console.error('Ошибка auth:', error);
        set((s) => ({
          messages: [
            ...s.messages,
            {
              id: crypto.randomUUID(),
              text: 'Произошла ошибка. Попробуй ещё раз.',
              sender: 'ai',
              timestamp: Date.now(),
            },
          ],
          isTyping: false,
        }));
      }
    } else {
      wsService.sendMessage(text);
    }
  },

  clearHistory: () => {
    set({ messages: [] });
  },

  logout: () => {
    authService.logout();
    set({
      isAuthenticated: false,
      username: null,
      userId: null,
      messages: [{
        id: crypto.randomUUID(),
        text: 'Привет! Я твой финансовый помощник. Как тебя зовут?',
        sender: 'ai',
        timestamp: Date.now()
      }]
    });
  },
}));