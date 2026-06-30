from fastapi import APIRouter, UploadFile, File, HTTPException
from pydantic import BaseModel
from app.services.llm_router import LLMRouter
from app.services.speech import transcribe_audio, text_to_speech
from app.services.qr_parser import parse_qr_code
import json
import re
import logging
import random
from fastapi import UploadFile, File, HTTPException
from app.services.ocr_service import extract_text_from_receipt
from app.services.receipt_analyzer import analyze_receipt_text

logger = logging.getLogger(__name__)
router = APIRouter()

# =========================================================================
# МОДЕЛИ ДАННЫХ
# =========================================================================
class ReceiptPhotoResponse(BaseModel):
    items: list[dict]
    total_amount: float | None
    store_name: str | None
class ParseRequest(BaseModel):
    text: str
    history: str = ""  #  Добавили поле для истории (опциональное)

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
    fn: str | None

class AuthChatRequest(BaseModel):
    session_id: str
    text: str

# =========================================================================
# СЛОВАРЬ ДЛЯ SEED PHRASE (100 простых русских слов)
# =========================================================================

SEED_WORD_LIST = [
    "яблоко", "река", "солнце", "книга", "стол", "окно", "дерево", "кошка", 
    "музыка", "город", "ветер", "звезда", "дорога", "море", "гора", "лес", 
    "дом", "машина", "чашка", "телефон", "ключ", "часы", "лампа", "зеркало",
    "подушка", "ковёр", "картина", "цветок", "птица", "рыба", "собака", "лошадь",
    "корова", "свинья", "утка", "гусь", "заяц", "волк", "медведь", "лиса",
    "тигр", "слон", "жираф", "обезьяна", "попугай", "ворона", "орёл", "акула",
    "кит", "дельфин", "черепаха", "краб", "медуза", "планета", "луна",
    "комета", "ракета", "самолёт", "поезд", "корабль", "велосипед", "самокат",
    "коньки", "лыжи", "мяч", "ручка", "карандаш", "ножницы", "клей",
    "бумага", "картон", "ткань", "нитка", "иголка", "пуговица", "молния", "карман",
    "шапка", "шарф", "перчатки", "ботинки", "носки", "рубашка", "брюки", "платье",
    "юбка", "куртка", "пальто", "шуба", "свитер", "жилет", "галстук", "ремень"
]

# Временное хранилище сессий
active_sessions = {}

# =========================================================================
# ПРОМПТЫ
# =========================================================================

FINANCIAL_PARSE_SYSTEM_PROMPT = """Ты — финансовый ассистент. Анализируй сообщение пользователя и определяй, содержит ли оно финансовую операцию.

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
4. Поле reply должно содержать дружелюбный ответ пользователю.
5.  Учитывай контекст предыдущих сообщений при ответе. Если пользователь ссылается на что-то из истории ("ещё", "тоже", "ещё раз"), понимай это в контексте.

ВАЖНО:
- Всегда возвращай валидный JSON
- Никогда не добавляй markdown или комментарии
"""

GENERAL_CHAT_SYSTEM_PROMPT = """Ты дружелюбный семейный финансовый консультант "ФинСоветник AI".
Отвечай кратко, тепло, с юмором. Используй эмодзи.
Учитывай контекст предыдущих сообщений, если они есть."""

def format_user_prompt(text: str, history: str) -> str:
    """Формирует user_prompt с учётом истории"""
    if history and history.strip() and "история сообщений пуста" not in history.lower():
        return f"{history}\n\nТекущее сообщение пользователя: {text}"
    else:
        return f"Текущее сообщение пользователя: {text}"

async def call_general_chat(text: str, history: str) -> str:
    """Вызывает общий чат с LLM"""
    user_prompt = format_user_prompt(text, history)
    
    try:
        response = await LLMRouter.call_llm(
            system_prompt=GENERAL_CHAT_SYSTEM_PROMPT,
            user_prompt=user_prompt,
            temperature=0.7,
            max_tokens=1000
        )
        return response
    except Exception as e:
        logger.error(f"Error in general chat: {e}")
        return "Извини, не смог ответить. Попробуй ещё раз! 🤔"
# =========================================================================
# ЭНДПОИНТЫ
# =========================================================================

@router.get("/")
async def root():
    """Health check"""
    return {
        "service": "ФинСоветник AI Service",
        "status": "running",
        "version": "1.0.0"
    }

@router.post("/parse")
async def parse_expense(request: ParseRequest):
    """Распознавание финансовых операций из текста с учётом контекста."""
    
    user_prompt = format_user_prompt(request.text, request.history)
    
    raw_response = ""
    
    try:
        raw_response = await LLMRouter.call_llm(
            system_prompt=FINANCIAL_PARSE_SYSTEM_PROMPT,
            user_prompt=user_prompt,
            temperature=0.3,
            max_tokens=1000
        )
        
        cleaned = re.sub(r'^```json\s*|\s*```$', '', raw_response, flags=re.MULTILINE).strip()
        cleaned = re.sub(r'//.*$', '', cleaned, flags=re.MULTILINE)
        
        parsed_data = json.loads(cleaned)
        
        if "is_financial" not in parsed_data:
            parsed_data["is_financial"] = False
        if "transactions" not in parsed_data:
            parsed_data["transactions"] = []
        if "reply" not in parsed_data:
            parsed_data["reply"] = "Готово!" if parsed_data["is_financial"] else "Понял!"
            
        if not parsed_data["is_financial"]:
            logger.info("💬 Не финансовая операция, запускаем общий чат")
            
            general_reply = await call_general_chat(request.text, request.history)
            parsed_data["reply"] = general_reply
            
            logger.info(f"💬 Общий чат ответ: {general_reply[:100]}...")
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
    """Распознавание голосового сообщения."""
    try:
        audio_data = await file.read()
        text = await transcribe_audio(audio_data)
        logger.info(f"🎤 Transcribed: {text}")
        return TranscribeResponse(text=text)
    except Exception as e:
        logger.error(f"Transcription error: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка распознавания: {str(e)}")

@router.post("/tts", response_model=TTSResponse)
async def text_to_speech_endpoint(request: ParseRequest):
    """Преобразование текста в голос."""
    try:
        audio_base64 = await text_to_speech(request.text)
        return TTSResponse(audio_base64=audio_base64, format="mp3")
    except Exception as e:
        logger.error(f"TTS error: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка генерации голоса: {str(e)}")

@router.post("/qr/parse", response_model=QRParseResponse)
async def parse_qr(file: UploadFile = File(...)):
    """Парсинг QR-кода с чека."""
    try:
        image_data = await file.read()
        result = await parse_qr_code(image_data)
        return QRParseResponse(**result)
    except Exception as e:
        logger.error(f"QR parse error: {e}")
        raise HTTPException(status_code=500, detail=f"Ошибка чтения QR: {str(e)}")

@router.post("/chat")
async def general_chat(request: ParseRequest):
    """Общий чат с AI-агентом с учётом контекста."""
    
    # ✅ Формируем user_prompt с учётом истории
    if request.history and request.history.strip() and "история сообщений пуста" not in request.history.lower():
        user_prompt = f"{request.history}\n\nТекущее сообщение пользователя: {request.text}"
    else:
        user_prompt = f"Текущее сообщение пользователя: {request.text}"
    
    try:
        response = await LLMRouter.call_llm(
            system_prompt=GENERAL_CHAT_SYSTEM_PROMPT,
            user_prompt=user_prompt,
            temperature=0.7,
            max_tokens=1000
        )
        
        return {"reply": response}
        
    except Exception as e:
        logger.error(f"Chat error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# =========================================================================
# ЭНДПОИНТ ДЛЯ РЕГИСТРАЦИИ/ВХОДА
# =========================================================================
@router.post("/auth/chat")
async def auth_chat(request: AuthChatRequest):
    session_id = request.session_id
    user_text = request.text.lower().strip()
    
    # Создаём сессию, если её нет
    if session_id not in active_sessions:
        active_sessions[session_id] = {
            "state": "ASK_NAME",
            "username": None,
            "seed_phrase": None,
            "is_existing": False
        }
    
    session = active_sessions[session_id]
    
    # =========================================================================
    # СОСТОЯНИЕ 1: Спрашиваем имя
    # =========================================================================
    if session["state"] == "ASK_NAME":
        session["username"] = user_text.capitalize()
        session["state"] = "ASK_IF_EXISTING"
        
        return {
            "reply": f"Приятно познакомиться, {session['username']}! 🎉 Ты уже регистрировался у нас раньше?",
            "state": "ASK_IF_EXISTING"
        }

    # =========================================================================
    # СОСТОЯНИЕ 2: Спрашиваем, новый ли он
    # =========================================================================
    elif session["state"] == "ASK_IF_EXISTING":
        if any(word in user_text for word in ["да", "ага", "уже", "вернулся", "входил"]):
            session["is_existing"] = True
            session["state"] = "VERIFY_SEED"
            return {
                "reply": f"Рад тебя видеть снова, {session['username']}! 🙌 Назови свою фразу доступа (12 слов).",
                "state": "VERIFY_SEED"
            }
        elif any(word in user_text for word in ["нет", "новый", "впервые", "первый раз"]):
            session["is_existing"] = False
            session["state"] = "WAITING_CONFIRMATION"
            
            seed_words = random.sample(SEED_WORD_LIST, 12)
            session["seed_phrase"] = " ".join(seed_words)
            
            return {
                "reply": f"Отлично! Тогда давай создадим твой аккаунт. 🔑 Запиши эти 12 слов в надежном месте:\n\n🔑 {session['seed_phrase']}\n\nКогда запишешь, напиши 'Готово'.",
                "state": "WAITING_CONFIRMATION",
                "seed_words": seed_words
            }
        else:
            return {
                "reply": "Ответь, пожалуйста, 'Да' или 'Нет'. Ты уже регистрировался у нас?",
                "state": "ASK_IF_EXISTING"
            }

    # =========================================================================
    # СОСТОЯНИЕ 3: Ждём подтверждения записи (только для НОВЫХ)
    # =========================================================================
    elif session["state"] == "WAITING_CONFIRMATION":
        if "готово" in user_text or "записал" in user_text:
            session["state"] = "VERIFY_SEED"
            return {
                "reply": "Отлично! Теперь для проверки напиши или продиктуй эти 12 слов по порядку.",
                "state": "VERIFY_SEED"
            }
        else:
            return {
                "reply": "Напиши 'Готово', когда запишешь слова.",
                "state": "WAITING_CONFIRMATION"
            }

    # =========================================================================
    # СОСТОЯНИЕ 4: Проверяем seed phrase
    # =========================================================================
    elif session["state"] == "VERIFY_SEED":
        # ✅ Если пользователь передумал и хочет зарегистрироваться заново
        if any(word in user_text for word in ["заново", "новый", "регистрация", "другой", "не помню", "забыл"]):
            # Сбрасываем сессию
            active_sessions[session_id] = {
                "state": "ASK_NAME",
                "username": None,
                "seed_phrase": None,
                "is_existing": False
            }
            return {
                "reply": "Хорошо! Давай начнём сначала. 🔄 Как тебя зовут?",
                "state": "ASK_NAME"
            }
        
        clean_text = re.sub(r'[^\w\s]', '', user_text)
        user_words = clean_text.split()
        
        # Для НОВОГО пользователя
        if not session.get("is_existing", False):
            stored_words = session["seed_phrase"].split() if session["seed_phrase"] else []
            
            if user_words == stored_words:
                session["state"] = "AUTHENTICATED"
                return {
                    "reply": f"✅ Всё верно! Добро пожаловать, {session['username']}!",
                    "state": "AUTHENTICATED",
                    "username": session["username"],
                    "authenticated": True,
                    "seed_phrase": session["seed_phrase"]
                }
            else:
                return {
                    "reply": f"❌ Слова не совпадают. Попробуй еще раз:\n\n🔑 {session['seed_phrase']}",
                    "state": "VERIFY_SEED"
                }
        else:
            # Для СУЩЕСТВУЮЩЕГО пользователя — отправляем на проверку в Java
            return {
                "reply": "",
                "state": "VERIFY_EXISTING",
                "username": session["username"],
                "seed_phrase": " ".join(user_words),
                "check_existing": True
            }

    # =========================================================================
    # СОСТОЯНИЕ 5: Проверка не удалась — предлагаем варианты
    # =========================================================================
    elif session["state"] == "OFFER_REREGISTER":
        # Пользователь хочет попробовать ещё раз
        if any(word in user_text for word in ["да", "попробую", "ещё", "еще", "повторить"]):
            session["state"] = "VERIFY_SEED"
            return {
                "reply": "Хорошо, давай попробуем ещё раз. Назови свою фразу доступа (12 слов):",
                "state": "VERIFY_SEED"
            }
        # Пользователь хочет зарегистрироваться заново
        elif any(word in user_text for word in ["нет", "заново", "новый", "регистрация", "другой", "забыл"]):
            # Сбрасываем сессию
            active_sessions[session_id] = {
                "state": "ASK_NAME",
                "username": None,
                "seed_phrase": None,
                "is_existing": False
            }
            return {
                "reply": "Хорошо! Давай начнём сначала. 🔄 Как тебя зовут?",
                "state": "ASK_NAME"
            }
        else:
            return {
                "reply": "Ответь 'Да' (попробовать ещё раз) или 'Нет' (зарегистрироваться заново).",
                "state": "OFFER_REREGISTER"
            }
    # =========================================================================
    # СОСТОЯНИЕ 6: Успешная аутентификация
    # =========================================================================
    elif session["state"] == "AUTHENTICATED":
        return {
            "reply": f"{session['username']}, ты уже вошел!",
            "state": "AUTHENTICATED",
            "username": session["username"],
            "authenticated": True
        }
        
    return {"reply": "Я тебя не понимаю.", "state": "ERROR"}

@router.post("/receipt/analyze-photo", response_model=ReceiptPhotoResponse)
async def analyze_receipt_photo(file: UploadFile = File(...)):
    """Анализирует фото чека: OCR + LLM"""
    try:
        image_data = await file.read()
        
        # 1. OCR
        raw_text = await extract_text_from_receipt(image_data)
        
        # 2. Анализ через LLM
        analysis = await analyze_receipt_text(raw_text)
        
        return ReceiptPhotoResponse(
            items=analysis.get("items", []),
            total_amount=analysis.get("total_amount"),
            store_name=analysis.get("store_name")
        )
        
    except Exception as e:
        logger.error(f"Ошибка анализа фото: {e}")
        raise HTTPException(status_code=500, detail=str(e))
