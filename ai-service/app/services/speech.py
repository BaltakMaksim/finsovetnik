import io
import logging
from typing import Optional

logger = logging.getLogger(__name__)

async def transcribe_audio(audio_data: bytes) -> str:
    """
    Распознавание речи через Whisper (локально или API).
    """
    # Пока заглушка - потом подключим Whisper
    logger.warning("⚠️ Transcription not implemented yet")
    return "[Голосовое сообщение]"

async def text_to_speech(text: str) -> str:
    """
    Генерация голоса через Edge TTS.
    Возвращает base64-encoded MP3.
    """
    # Пока заглушка - потом подключим Edge TTS
    logger.warning("⚠️ TTS not implemented yet")
    return ""