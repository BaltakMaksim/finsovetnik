// src/store/useChatStore.ts
import { create } from 'zustand';
import { wsService } from '@services/websocket';

export interface Transaction {
  amount: number;
  category: string;
  owner: string;
}

export interface Message {
  id: string;
  text: string;
  sender: 'user' | 'ai';
  timestamp: number;
  transactions?: Transaction[];
}

interface ChatState {
  messages: Message[];
  isConnected: boolean;
  isTyping: boolean;

  // Actions
  connect: () => void;
  disconnect: () => void;
  sendMessage: (text: string) => void;
  clearHistory: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  isConnected: false,
  isTyping: false,

  connect: () => {
    wsService.connect(
      // onMessage: получаем ответ от AI
      (response: Record<string, unknown>) => {
        const aiMessage: Message = {
          id: crypto.randomUUID(),
          text: (response.reply as string) || 'Нет ответа',
          sender: 'ai',
          timestamp: Date.now(),
          transactions: response.transactions as Transaction[] | undefined,
        };

        set((state) => ({
          messages: [...state.messages, aiMessage],
          isTyping: false,
        }));
      },
      // onStatusChange: обновляем статус подключения
      (connected: boolean) => {
        set({ isConnected: connected });
      }
    );
  },

  disconnect: () => {
    wsService.disconnect();
    set({ isConnected: false });
  },

  sendMessage: (text: string) => {
    if (!text.trim()) return;

    // Добавляем сообщение пользователя в локальный стейт
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

    // Отправляем через WebSocket
    wsService.sendMessage(text);
  },

  clearHistory: () => {
    set({ messages: [] });
  },
}));