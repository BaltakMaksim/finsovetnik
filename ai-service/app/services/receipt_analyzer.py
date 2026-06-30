import json
import re
import logging
from app.services.llm_router import LLMRouter

logger = logging.getLogger(__name__)

RECEIPT_ANALYSIS_PROMPT = """Ты — финансовый аналитик. Анализируй текст чека и извлекай список покупок.

Верни результат СТРОГО в формате JSON (без markdown, без комментариев):
{
  "items": [
    {
      "name": "название товара",
      "quantity": 1,
      "price": 150.00,
      "sum": 150.00,
      "category": "категория"
    }
  ],
  "total_amount": 350.50,
  "store_name": "название магазина"
}

ПРАВИЛА КАТЕГОРИЗАЦИИ (используй ТОЛЬКО эти):
- "Продукты" — еда, напитки, продукты питания
- "Кафе и рестораны" — кафе, рестораны, кофе, фастфуд
- "Транспорт" — такси, бензин, метро
- "Аптека" — лекарства, медицина
- "Одежда" — одежда, обувь
- "Развлечения" — кино, игры, хобби
- "Дом" — бытовая химия, мебель
- "Связь" — телефон, интернет
- "Услуги" — стрижка, салон
- "Другое" — если не подходит

ПРАВИЛА:
1. Извлекай ВСЕ товары.
2. Если количество не указано — ставь 1.
3. total_amount — итоговая сумма чека.

ВАЖНО: Всегда возвращай валидный JSON. Никакого markdown.
"""

async def analyze_receipt_text(text: str) -> dict:
    """Анализирует текст чека через LLM"""
    try:
        raw_response = await LLMRouter.call_llm(
            system_prompt=RECEIPT_ANALYSIS_PROMPT,
            user_prompt=f"Текст чека:\n{text}",
            temperature=0.2,
            max_tokens=2000
        )
        
        # Очищаем от markdown (если LLM всё же добавил)
        cleaned = re.sub(r'^```json\s*|\s*```$', '', raw_response, flags=re.MULTILINE).strip()
        parsed = json.loads(cleaned)
        
        logger.info(f" LLM извлёк {len(parsed.get('items', []))} товаров")
        return parsed
        
    except Exception as e:
        logger.error(f"Ошибка анализа чека: {e}")
        raise Exception(f"Не удалось проанализировать чек: {str(e)}")