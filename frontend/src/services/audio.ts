class AudioService {
  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];
  private stream: MediaStream | null = null;

  /**
   * Начинает запись голоса
   */
  async startRecording(): Promise<void> {
    try {
      // Запрашиваем доступ к микрофону
      this.stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          sampleRate: 16000
        } 
      });
      
      this.mediaRecorder = new MediaRecorder(this.stream);
      this.audioChunks = [];

      // Собираем аудио-чанки
      this.mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data);
        }
      };

      this.mediaRecorder.start();
      console.log('🎤 Запись начата');
    } catch (error) {
      console.error('❌ Ошибка доступа к микрофону:', error);
      throw new Error('Не удалось получить доступ к микрофону. Проверьте разрешения браузера.');
    }
  }

  /**
   * Останавливает запись и возвращает аудиофайл
   */
  async stopRecording(): Promise<Blob> {
    if (!this.mediaRecorder || this.mediaRecorder.state === 'inactive') {
      throw new Error('Запись не активна');
    }

    return new Promise((resolve) => {
      this.mediaRecorder!.onstop = () => {
        const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
        
        // Останавливаем поток микрофона
        if (this.stream) {
          this.stream.getTracks().forEach(track => track.stop());
          this.stream = null;
        }
        this.mediaRecorder = null;
        this.audioChunks = [];
        
        resolve(audioBlob);
      };

      this.mediaRecorder!.stop();
      console.log('🎤 Запись остановлена');
    });
  }

  /**
   * Проверяет, поддерживается ли запись аудио
   */

  /**
   * Проверяет, идёт ли сейчас запись
   */
  isRecordingSupported(): boolean {
  return typeof MediaRecorder !== 'undefined' && 
         typeof navigator.mediaDevices?.getUserMedia === 'function';
}
}

export const audioService = new AudioService();