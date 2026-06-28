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
Ты — финансовый ассистент. Анализируй сообщение пользователя и определяй, содержит ли оно финансовую операцию.

Верни результат СТРОГО в формате JSON (без markdown, без комментариев):
{
  "is_financial": true/false,
  "transactions": [
    {"amount": число, "category": "строка", "type": "INCOME или EXPENSE"}
  ],
  "reply": "текстовый ответ пользователю"
}

Правила:
1. Если пользователь тратит, покупает, платит, получает деньги — это финансовая операция (is_financial: true).
2. Если пользователь просто общается, задает вопросы, благодарит — это не финансовая операция (is_financial: false, transactions: []).
3. Для финансовых операций:
   - Тратит/покупает/платит → type: "EXPENSE"
   - Получает/зарабатывает/возврат → type: "INCOME"
   - Если тип неясен, по умолчанию ставь "EXPENSE"
4. Поле chat_reply должно содержать дружелюбный ответ пользователю:
   - Для финансовых операций: подтверждение с суммой и категорией
   - Для обычных сообщений: текстовый ответ на вопрос/приветствие

Примеры:
"Купил кофе за 300" -> 
{
  "is_financial": true,
  "transactions": [{"amount": 300, "category": "Кафе", "type": "EXPENSE"}],
  "reply": "✅ Записал расход 300₽ на Кафе"
}

"Получил зарплату 100000" -> 
{
  "is_financial": true,
  "transactions": [{"amount": 100000, "category": "Зарплата", "type": "INCOME"}],
  "reply": "✅ Записал доход 100000₽ (Зарплата)"
}

"Купил хлеб за 50 и молоко за 80" -> 
{
  "is_financial": true,
  "transactions": [
    {"amount": 50, "category": "Продукты", "type": "EXPENSE"},
    {"amount": 80, "category": "Продукты", "type": "EXPENSE"}
  ],
  "reply": "✅ Записал 2 расхода на Продукты (50₽ и 80₽)"
}

"Привет, как дела?" -> 
{
  "is_financial": false,
  "transactions": [],
  "reply": "Привет! У меня всё отлично, спасибо! Чем могу помочь с финансами?"
}

"Спасибо!" -> 
{
  "is_financial": false,
  "transactions": [],
  "reply": "Пожалуйста! Обращайся, если нужно записать расходы или доходы 😊"
}

ВАЖНО:
- Всегда возвращай валидный JSON
- Никогда не добавляй markdown (```json) или комментарии
- Если не уверен, что это финансовая операция — ставь is_financial: false
"""
    
    raw_response = ""
    
    try:
        raw_response = await LLMRouter.call_llm(
            system_prompt=system_prompt,
            user_prompt=request.text,
            temperature=0.3,
            max_tokens=1000
        )
        
        # Очистка от markdown (на случай, если AI всё же добавит)
        cleaned = re.sub(r'^```json\s*|\s*```$', '', raw_response, flags=re.MULTILINE).strip()
        
        # Убираем возможные комментарии в JSON
        cleaned = re.sub(r'//.*$', '', cleaned, flags=re.MULTILINE)
        
        parsed_data = json.loads(cleaned)
        
        # Проверяем обязательные поля
        if "is_financial" not in parsed_data:
            parsed_data["is_financial"] = False
        if "transactions" not in parsed_data:
            parsed_data["transactions"] = []
        if "reply" not in parsed_data:
            parsed_data["reply"] = "Готово!" if parsed_data["is_financial"] else "Понял!"
        
        return parsed_data
        
    except json.JSONDecodeError as e:
        logger.error(f"JSON decode error: {e}, raw: {raw_response}")
        return {
            "is_financial": False,
            "transactions": [],
            "reply": "Не смог разобрать сообщение. Попробуйте сформулировать иначе! 🤔"
        }
    except Exception as e:
        logger.error(f"Error parsing expense: {e}")
        return {
            "is_financial": False,
            "transactions": [],
            "reply": f"Произошла ошибка при обработке. Попробуйте ещё раз!"
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