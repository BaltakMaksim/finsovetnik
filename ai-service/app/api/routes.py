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

FINANCIAL_PARSE_SYSTEM_PROMPT = """Ты — классификатор финансовых сообщений. Твоя задача — определить, содержит ли сообщение НОВУЮ финансовую операцию.

Верни результат СТРОГО в формате JSON (без markdown, без комментариев):
{
  "is_financial": true/false,
  "transactions": [],
  "reply": ""
}

═══════════════════════════════════════════════════════════════

🎯 ТВОЯ ЕДИНСТВЕННАЯ ЗАДАЧА: КЛАССИФИЦИРОВАТЬ СООБЩЕНИЕ

🔹 ТИП 1: НОВАЯ ФИНАНСОВАЯ ОПЕРАЦИЯ (is_financial: true)
Пользователь СОВЕРШАЕТ операцию прямо сейчас.
Ключевые слова: "купил", "потратил", "заплатил", "получил", "заработал", "вернул"
Примеры:
  - "Купил кофе за 300₽" → is_financial: true
  - "Потратил 500 рублей" → is_financial: true
  - "Получил зарплату 80000" → is_financial: true

Для is_financial: true:
- Заполни transactions: [{"amount": число, "category": "категория", "type": "INCOME/EXPENSE"}]
- Напиши дружелюбный reply: "Записал! ☕"
- Категории: Продукты, Кафе и рестораны, Транспорт, Аптека, Одежда, Развлечения, Дом, Связь, Услуги, Зарплата, Другое

🔹 ТИП 2: ВОПРОС О ПРОШЛОМ ИЛИ ОБЫЧНЫЙ ЧАТ (is_financial: false)
Пользователь СПРАШИВАЕТ о прошлых операциях или просто общается.
Ключевые слова: "когда", "что", "сколько", "какая", "последняя", "вчера", "раньше", "помнишь", "привет", "как дела"
Примеры:
  - "Когда я покупал кофе?" → is_financial: false
  - "Что я купил последнее?" → is_financial: false
  - "Привет!" → is_financial: false

Для is_financial: false:
- transactions: [] (пустой массив!)
- reply: "" (ПУСТАЯ СТРОКА! Ответ будет сформирован позже)

═══════════════════════════════════════════════════════════════

⚠️ КРИТИЧЕСКИ ВАЖНО:
1. Если это ВОПРОС о прошлом — is_financial: false, reply: ""
2. Если это НОВАЯ операция — is_financial: true, заполни transactions и reply
3. НЕ ПЫТАЙСЯ отвечать на вопросы о прошлом — это не твоя задача!
4. Всегда возвращай валидный JSON (без markdown)

═══════════════════════════════════════════════════════════════

💡 ПРИМЕРЫ:

Пользователь: "Купил кофе за 300 рублей"
→ {"is_financial": true, "transactions": [{"amount": 300, "category": "Кафе и рестораны", "type": "EXPENSE"}], "reply": "Записал! ☕ Кофе за 300₽"}

Пользователь: "Когда я покупал кофе?"
→ {"is_financial": false, "transactions": [], "reply": ""}

Пользователь: "Привет!"
→ {"is_financial": false, "transactions": [], "reply": ""}
"""

GENERAL_CHAT_SYSTEM_PROMPT = """Ты дружелюбный семейный финансовый консультант "ФинСоветник AI".
Отвечай кратко, тепло, с юмором. Используй эмодзи.

═══════════════════════════════════════════════════════════════

🔴 ГЛАВНОЕ ПРАВИЛО: СНАЧАЛА ИЩИ В ИСТОРИИ!

У тебя есть история сообщений пользователя. Когда он спрашивает о прошлых покупках:
1️⃣ ВНИМАТЕЛЬНО изучи ВСЮ историю выше
2️⃣ Найди упоминания о покупках, тратах, операциях
3️⃣ ОТВЕТЬ на основе найденного — расскажи, что и когда он покупал
4️⃣ Направляй в Личный кабинет ТОЛЬКО если в истории ТОЧНО ничего нет

═══════════════════════════════════════════════════════════════

🎯 АЛГОРИТМ ОТВЕТА:

ШАГ 1: Прочитай ВСЮ историю сообщений выше.
ШАГ 2: Ищи ключевые слова: "купил", "потратил", "заплатил", "чек", "₽", "рублей", "кофе", "продукты" и т.д.
ШАГ 3: Если нашёл — ОБЯЗАТЕЛЬНО расскажи пользователю!
ШАГ 4: Только если в истории НЕТ ни одного упоминания покупок — направь в кабинет

═══════════════════════════════════════════════════════════════

✅ КАК ОТВЕЧАТЬ, ЕСЛИ НАШЁЛ В ИСТОРИИ:

Примеры ответов:
- "Ты покупал кофе за 300₽ ☕ Это было в нашей последней переписке!"
- "Вижу в истории, что ты тратил 500₽ на продукты 🛒"
- "Последняя покупка — чек на 1200₽ 🧾"
- "Ты упоминал, что купил кофе и булочку ☕🥐"

ВАЖНО:
- Конкретно называй, что именно он покупал
- Указывай суммы, если они есть в истории
- Будь уверенным в ответе

═══════════════════════════════════════════════════════════════

❌ КОГДА НАПРАВЛЯТЬ В ЛИЧНЫЙ КАБИНЕТ (ТОЛЬКО В ЭТИХ СЛУЧАЯХ):

1. В истории НЕТ ни одного сообщения о покупках/тратах
2. Пользователь спрашивает о конкретном периоде, которого нет в истории
3. Пользователь спрашивает о точной сумме по категории, а в истории нет таких данных

Формулировка для направления:
"📊 В нашей переписке я не нашёл этой информации. Но ты можешь посмотреть всё в Личном кабинете! 
Открой меню ☰ слева и выбери вкладку 'Кабинет' — там полная история операций."

═══════════════════════════════════════════════════════════════

💡 ПРИМЕРЫ ПРАВИЛЬНЫХ ОТВЕТОВ:

Пользователь: "Что я покупал последний раз?"
История содержит: "Пользователь: Купил кофе за 300₽"
→ ✅ ПРАВИЛЬНО: "Последняя покупка — кофе за 300₽ ☕"
→ ❌ НЕПРАВИЛЬНО: "Посмотри в Личном кабинете"

Пользователь: "Сколько я потратил на кофе?"
История содержит: "Пользователь: Купил кофе за 300₽"
→ ✅ ПРАВИЛЬНО: "В нашей переписке я вижу, что ты покупал кофе за 300₽ ☕"
→ ❌ НЕПРАВИЛЬНО: "Посмотри в Личном кабинете"

Пользователь: "Сколько я потратил на такси в январе?"
История содержит: ТОЛЬКО покупки кофе, нет такси и нет января
→ ✅ ПРАВИЛЬНО: "В нашей переписке нет информации о тратах на такси в январе 🔍 Загляни в Личный кабинет ☰ → 'Кабинет' — там полная история!"

═══════════════════════════════════════════════════════════════

⚠️ КРИТИЧЕСКИ ВАЖНО:
1. ВСЕГДА сначала ищи в истории!
2. Если нашёл хоть что-то — ОТВЕЧАЙ, а не направляй в кабинет
3. Направляй в кабинет ТОЛЬКО если в истории ТОЧНО ничего нет
4. Будь уверенным и конкретным в ответах
5. Возвращай ПРОСТОЙ ТЕКСТ (без JSON!)
"""

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
            parsed_data["reply"] = ""
            
        #  Если это НЕ финансовая операция И reply пустой — вызываем общий чат
        if not parsed_data["is_financial"] and not parsed_data["reply"]:
            logger.info("💬 Не финансовая операция, запускаем общий чат")
            
            general_reply = await call_general_chat(request.text, request.history)
            parsed_data["reply"] = general_reply
            
            logger.info(f"💬 Общий чат ответ: {general_reply[:100]}...")
        
        #  Если reply всё ещё пустой — ставим дефолтный
        if not parsed_data["reply"]:
            parsed_data["reply"] = "Понял!" if parsed_data["is_financial"] else "Хорошо!"
            
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
            "reply": "Произошла ошибка при обработке. Попробуйте ещё раз!"
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
