import logging
from typing import Optional

logger = logging.getLogger(__name__)

async def parse_qr_code(image_data: bytes) -> dict:
    """
    Парсинг QR-кода с чека.
    Извлекает: сумму, дату, фискальный номер.
    """
    # Пока заглушка - потом подключим pyzbar/opencv
    logger.warning("⚠️ QR parsing not implemented yet")
    return {
        "raw_data": "",
        "amount": None,
        "date": None,
        "fn": None
    }