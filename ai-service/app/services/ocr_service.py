from PIL import Image, ImageEnhance, ImageFilter
import pytesseract
import io
import logging

logger = logging.getLogger(__name__)

async def extract_text_from_receipt(image_data: bytes) -> str:
    """Извлекает текст из фото чека с предобработкой"""
    try:
        image = Image.open(io.BytesIO(image_data))
        
        # Предобработка для лучшего распознавания
        image = image.convert('L')  # Чёрно-белый
        image = ImageEnhance.Contrast(image).enhance(2.0)
        image = ImageEnhance.Sharpness(image).enhance(1.5)
        
        # Распознаём текст (русский + английский)
        text = pytesseract.image_to_string(image, lang='rus+eng')
        
        logger.info(f" OCR распознал {len(text)} символов")
        return text
        
    except Exception as e:
        logger.error(f"Ошибка OCR: {e}")
        raise Exception(f"Не удалось распознать текст: {str(e)}")