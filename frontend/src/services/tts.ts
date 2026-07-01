const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

interface TtsResponse {
  audio_base64: string;
}

class TtsService {
  private currentAudio: HTMLAudioElement | null = null;
  private currentUrl: string | null = null;
  private abortController: AbortController | null = null;

  async textToSpeech(text: string): Promise<string> {
    // Отменяем предыдущий запрос, если он ещё летит
    if (this.abortController) {
      this.abortController.abort();
    }
    this.abortController = new AbortController();

    // Освобождаем предыдущий Blob URL
    this.revokeCurrentUrl();

    try {
      const response = await fetch(`${API_URL}/api/tts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
        signal: this.abortController.signal,
      });

      if (!response.ok) {
        throw new Error(`Ошибка генерации голоса: ${response.status}`);
      }

      const data: TtsResponse = await response.json();

      const byteCharacters = atob(data.audio_base64);
      const byteArray = new Uint8Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteArray[i] = byteCharacters.charCodeAt(i);
      }
      const blob = new Blob([byteArray], { type: 'audio/mp3' });

      this.currentUrl = URL.createObjectURL(blob);
      return this.currentUrl;
    } catch (error) {
      if ((error as Error).name === 'AbortError') return '';
      console.error('❌ Ошибка TTS:', error);
      throw error;
    }
  }

  async playAudio(audioUrl: string): Promise<void> {
    this.stopAudio();

    return new Promise((resolve, reject) => {
      const audio = new Audio(audioUrl);
      this.currentAudio = audio;

      audio.onended = () => {
        this.revokeCurrentUrl();
        this.currentAudio = null;
        resolve();
      };

      audio.onerror = () => {
        this.revokeCurrentUrl();
        this.currentAudio = null;
        reject(new Error('Ошибка воспроизведения'));
      };

      audio.play().catch((err) => {
        this.revokeCurrentUrl();
        this.currentAudio = null;
        reject(err);
      });
    });
  }

  /** Удобный метод: сгенерировать и сразу озвучить */
  async speak(text: string): Promise<void> {
    const url = await this.textToSpeech(text);
    if (url) await this.playAudio(url);
  }

  stopAudio(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    if (this.currentAudio) {
      this.currentAudio.pause();
      this.currentAudio = null;
    }
    this.revokeCurrentUrl();
  }

  private revokeCurrentUrl(): void {
    if (this.currentUrl) {
      URL.revokeObjectURL(this.currentUrl);
      this.currentUrl = null;
    }
  }
}

export const ttsService = new TtsService();