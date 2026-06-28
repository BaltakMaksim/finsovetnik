const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

class TtsService {
  // Храним ссылку на текущий аудио-элемент, чтобы можно было остановить предыдущий звук
  private currentAudio: HTMLAudioElement | null = null;

  /**
   * Отправляет текст на бэкенд и возвращает URL для воспроизведения
   */
  async textToSpeech(text: string): Promise<string> {
    try {
      const response = await fetch(`${API_URL}/api/tts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
      });

      if (!response.ok) {
        throw new Error('Ошибка генерации голоса');
      }

      const data = await response.json();
      
      // Python возвращает аудио в виде base64 строки. 
      // Нам нужно превратить её в Blob, чтобы браузер мог это проиграть.
      const byteCharacters = atob(data.audio_base64);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: 'audio/mp3' });
      
      // Создаём временную ссылку на этот Blob в памяти браузера
      return URL.createObjectURL(blob);
    } catch (error) {
      console.error('❌ Ошибка TTS:', error);
      throw error;
    }
  }

  /**
   * Проигрывает аудио по переданному URL
   */
  async playAudio(audioUrl: string): Promise<void> {
    // Если что-то уже играет — останавливаем, чтобы звуки не накладывались
    if (this.currentAudio) {
      this.currentAudio.pause();
      this.currentAudio = null;
    }

    return new Promise((resolve, reject) => {
      const audio = new Audio(audioUrl);
      this.currentAudio = audio;
      
      audio.onended = () => {
        this.currentAudio = null;
        resolve();
      };
      
      audio.onerror = () => {
        this.currentAudio = null;
        reject(new Error('Ошибка воспроизведения'));
      };
      
      audio.play().catch((err) => {
        this.currentAudio = null;
        reject(err);
      });
    });
  }

  /**
   * Принудительно останавливает текущее воспроизведение
   */
  stopAudio(): void {
    if (this.currentAudio) {
      this.currentAudio.pause();
      this.currentAudio = null;
    }
  }
}

export const ttsService = new TtsService();