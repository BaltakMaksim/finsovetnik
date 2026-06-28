import io
import logging
import base64
import edge_tts
from faster_whisper import WhisperModel
from typing import Optional

logger = logging.getLogger(__name__)

# Голос для TTS
RUSSIAN_VOICE = "ru-RU-SvetlanaNeural"

# Модель Whisper для распознавания речи
# "base" - быстрая и точная для русского языка
# При первом запуске модель скачается автоматически (~150 MB)
WHISPER_MODEL = WhisperModel("base", device="cpu", compute_type="int8")

async def transcribe_audio(audio_data: bytes) -> str:
    """
    Распознавание речи через Whisper (локально).
    """
    try:
        logger.info(f" Начинаем распознавание аудио, размер: {len(audio_data)} байт")
        
        # Сохраняем аудио во временный файл
        audio_buffer = io.BytesIO(audio_data)
        audio_buffer.seek(0)
        
        # Распознаём речь
        segments, _ = WHISPER_MODEL.transcribe(audio_buffer, language="ru", beam_size=5)
        
        # Собираем текст из всех сегментов
        recognized_text = ""
        for segment in segments:
            recognized_text += segment.text + " "
        
        recognized_text = recognized_text.strip()
        
        logger.info(f"✅ Распознанный текст: {recognized_text}")
        return recognized_text
        
    except Exception as e:
        logger.error(f"❌ Ошибка распознавания речи: {e}")
        raise

async def text_to_speech(text: str) -> str:
    """
    Генерация голоса через Edge TTS.
    Возвращает base64-encoded MP3.
    """
    try:
        logger.info(f" Генерация голоса для текста: {text[:50]}...")
        
        communicate = edge_tts.Communicate(text, RUSSIAN_VOICE)
        audio_buffer = io.BytesIO()
        
        async for chunk in communicate.stream():
            if chunk.get("type") == "audio" and "data" in chunk:
                audio_buffer.write(chunk["data"])
        
        audio_bytes = audio_buffer.getvalue()
        audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
        
        logger.info(f"✅ Аудио сгенерировано, размер: {len(audio_bytes)} байт")
        return audio_base64
        
    except Exception as e:
        logger.error(f"❌ Ошибка генерации голоса: {e}")
        raise