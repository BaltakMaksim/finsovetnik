export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface AuthResponse {
  reply: string;
  state: string;
  seed_words?: string[];
  username?: string;
  authenticated?: boolean;
  user_id?: number;
  access_token?: string;
  refresh_token?: string;
}

class AuthService {
  private sessionId: string | null = null;

  constructor() {
    this.sessionId = localStorage.getItem('session_id');
    if (!this.sessionId) {
      this.sessionId = this.generateSessionId();
      localStorage.setItem('session_id', this.sessionId);
    }
  }

  private generateSessionId(): string {
    return 'session_' + Math.random().toString(36).substring(2, 15) + Date.now().toString(36);
  }

  async sendMessage(text: string): Promise<AuthResponse> {
    if (!this.sessionId) throw new Error('Session ID не инициализирован');

    const response = await fetch(`${API_URL}/api/auth-chat/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ session_id: this.sessionId, text })
    });

    if (!response.ok) throw new Error('Ошибка сервера: ' + response.status);
    return await response.json();
  }

  

  saveAuth(username: string, userId: number, accessToken: string, refreshToken: string) {
    localStorage.setItem('auth_username', username);
    localStorage.setItem('auth_user_id', userId.toString());
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
  }

  getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('refresh_token');
  }

  getUsername(): string | null {
    return localStorage.getItem('auth_username');
  }

  getUserId(): number | null {
    const id = localStorage.getItem('auth_user_id');
    return id ? Number(id) : null;
  }

  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  }

  clearTokens() {
    localStorage.removeItem('auth_username');
    localStorage.removeItem('auth_user_id');
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
  }

  logout() {
    this.clearTokens();
    // Генерируем новый session_id, чтобы начать с чистого листа
    this.sessionId = this.generateSessionId();
    localStorage.setItem('session_id', this.sessionId);
  }
  async tryAutoLogin(): Promise<{ username: string; userId: number; accessToken: string; refreshToken: string } | null> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) return null;

    try {
      const response = await fetch(`${API_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refresh_token: refreshToken })
      });

      if (!response.ok) {
        // Токен истёк или невалиден — очищаем
        this.clearTokens();
        return null;
      }

      const data = await response.json();
      
      // Сохраняем новые токены
      this.saveAuth(
        data.username,
        data.user_id,
        data.access_token,
        data.refresh_token
      );

      return {
        username: data.username,
        userId: data.user_id,
        accessToken: data.access_token,
        refreshToken: data.refresh_token
      };
    } catch (error) {
      console.error('Ошибка авто-входа:', error);
      this.clearTokens();
      return null;
    }
  }
}

export const authService = new AuthService();