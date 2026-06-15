/// <reference types="vite/client" />

// ==========================================
// Декларации для SCSS Modules
// ==========================================
declare module '*.module.scss' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

// Декларация для обычных SCSS файлов (глобальные стили)
declare module '*.scss';

// ==========================================
// Декларации для изображений
// ==========================================
declare module '*.svg' {
  const content: string;
  export default content;
}

declare module '*.png' {
  const content: string;
  export default content;
}

declare module '*.jpg' {
  const content: string;
  export default content;
}

// ==========================================
// Декларация для sockjs-client
// ==========================================
declare module 'sockjs-client' {
  class SockJS {
    constructor(url: string, _reserved?: unknown, options?: unknown);
    close(code?: number, reason?: string): void;
    send(data: string): void;
    onopen: ((event: Event) => void) | null;
    onmessage: ((event: MessageEvent) => void) | null;
    onclose: ((event: CloseEvent) => void) | null;
    onerror: ((event: Event) => void) | null;
    readyState: number;
    protocol: string;
    url: string;
  }
  export = SockJS;
}