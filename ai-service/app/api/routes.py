from fastapi import APIRouter, UploadFile, File, HTTPException
from pydantic import BaseModel
from app.services.llm_router import LLMRouter
from app.services.speech import transcribe_audio, text_to_speech
from app.services.qr_parser import parse_qr_code
import json
import re
import logging

logger = logging.getLogger(__name__)
router = APIRouter()

# =========================================================================
# МОДЕЛИ ДАННЫХ
# =========================================================================

class ParseRequest(BaseModel):
    text: str

class ParseResponse(BaseModel):
    amount: float
    category: str
    owner: str
    reply: str

class TranscribeResponse(BaseModel):
    text: str

class TTSResponse(BaseModel):
    audio_base64: str
    format: str

class QRParseResponse(BaseModel):
    raw_data: str
    amount: float | None
    date: str | None
    fn: str | None  # фискальный номер

# =========================================================================
# ЭНДПОИНТЫ
# =========================================================================

@router.get("/")
async def root():
    """Health check - проверка работоспособности сервиса"""
    return {
        "service": "ФинСоветник AI Service",
        "status": "running",
        "version": "1.0.0"
    }

@router.post("/parse")
async def parse_expense(request: ParseRequest):
    """
    Распознавание финансовых операций из текста.
    Поддерживает несколько транзакций в одном сообщении.
    """
    system_prompt = """
    Ты семейный финансовый агент "ФинСоветник".
    
    ТВОЯ ЗАДАЧА:
    - Если текст содержит финансовые операции (покупки, траты, платежи) - извлеки их в JSON.
    - Если текст НЕ содержит финансовых операций (просто разговор, приветствие) - верни флаг is_financial: false.
    
    ФОРМАТ ОТВЕТА (СТРОГО JSON, без markdown):
    {
        "is_financial": true/false,
        "transactions": [
            {
                "amount": число,
                "category": "Еда|Транспорт|Развлечения|Животные|Быт|Кафе|Одежда|Здоровье|Кредиты|Прочее",
                "owner": "Максим|Лида|Общие",
                "reply": "короткий ответ с эмодзи"
            }
        ],
        "chat_reply": "если is_financial=false, то ответ как чат-бот"
    }
    
    ПРИМЕРЫ:
    1. "Купил корм коту 800 рублей" →
       {"is_financial": true, "transactions": [{"amount": 800, "category": "Животные", "owner": "Максим", "reply": "Записал 800₽ на корм 🐕"}], "chat_reply": ""}
    
    2. "Заправил машину на 2000 и купил кофе 250р" →
       {"is_financial": true, "transactions": [{"amount": 2000, "category": "Транспорт", "owner": "Максим", "reply": "2000₽ на бензин ⛽"}, {"amount": 250, "category": "Кафе", "owner": "Максим", "reply": "250₽ на кофе ☕"}], "chat_reply": ""}
    
    3. "Привет, как дела?" →
       {"is_financial": false, "transactions": [], "chat_reply": "Привет! Я ФинСоветник, готов помочь с учётом расходов 😊"}
    """
    
    raw_response = ""
    
    try:
        raw_response = await LLMRouter.call_llm(
            system_prompt=system_prompt,
            user_prompt=request.text,
            temperature=0.3,
            max_tokens=1000
        )
        
        # Очистка от markdown
        cleaned = re.sub(r'^```json\s*|\s*```$', '', raw_response, flags=re.MULTILINE).strip()
        parsed_data = json.loads(cleaned)
        
        # Проверяем, есть ли обязательное поле
        if "is_financial" not in parsed_data:
            raise ValueError("Отсутствует поле is_financial")
        
        return parsed_data
        
    except json.JSONDecodeError as e:
        logger.error(f"JSON decode error: {e}, raw: {raw_response}")
        # Fallback: возвращаем дефолтный ответ
        return {
            "is_financial": False,
            "transactions": [],
            "chat_reply": "Не смог разобрать сообщение. Попробуйте ещё раз! 🤔"
        }
    except Exception as e:
        logger.error(f"Error parsing expense: {e}")
        return {
            "is_financial": False,
            "transactions": [],
            "chat_reply": f"Ошибка: {str(e)}"
        }
        
@router.post("/transcribe", response_model=TranscribeResponse)
async def transcribe(file: UploadFile = File(...)):
    """
    Распознавание голосового сообщения (Speech-to-Text).
    Принимает аудиофайл (ogg, mp3, wav) → возвращает текст.
    """
    try:
        # Чтение файла
        audio_data = await file.read()
        
        # Распознавание через Whisper
        text = await transcribe_audio(audio_data)
        
        logger.info(f"🎤 Transcribed: {text}")
        return TranscribeResponse(text=text)
        
    except Exception as e:
        logger.error(f"Transcription error: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка распознавания: {str(e)}")

@router.post("/tts", response_model=TTSResponse)
async def text_to_speech_endpoint(text: str):
    """
    Преобразование текста в голос (Text-to-Speech).
    Возвращает base64-encoded аудио для озвучки ответов агента.
    """
    try:
        audio_base64 = await text_to_speech(text)
        return TTSResponse(audio_base64=audio_base64, format="mp3")
    except Exception as e:
        logger.error(f"TTS error: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка генерации голоса: {str(e)}")

@router.post("/qr/parse", response_model=QRParseResponse)
async def parse_qr(file: UploadFile = File(...)):
    """
    Парсинг QR-кода с чека.
    Извлекает фискальные данные: сумму, дату, фискальный номер.
    """
    try:
        # Чтение изображения
        image_data = await file.read()
        
        # Парсинг QR
        result = await parse_qr_code(image_data)
        
        return QRParseResponse(**result)
        
    except Exception as e:
        logger.error(f"QR parse error: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка чтения QR: {str(e)}")

@router.post("/chat")
async def general_chat(request: ParseRequest):
    """
    Общий чат с AI-агентом (не финансовые вопросы).
    Для общения, советов, вопросов о бюджете.
    """
    system_prompt = """
    Ты дружелюбный семейный финансовый консультант "ФинСоветник".
    Отвечай кратко, тепло, с юмором. Используй эмодзи.
    
    Ты помогаешь семье наладить финансы.
    Если спрашивают про бюджет - давай общие советы, но не давай конкретных цифр
    (ты не имеешь доступа к базе данных).
    """
    
    try:
        response = await LLMRouter.call_llm(
            system_prompt=system_prompt,
            user_prompt=request.text,
            temperature=0.7,
            max_tokens=1000
        )
        
        return {"reply": response}
        
    except Exception as e:
        logger.error(f"Chat error: {e}")
        raise HTTPException(status_code=500, detail=str(e))