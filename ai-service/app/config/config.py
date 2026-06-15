import os
from pathlib import Path
from dotenv import load_dotenv
from typing import Optional


BASE_DIR = Path(__file__).resolve().parent.parent.parent
ENV_PATH = BASE_DIR / ".env"


load_dotenv(dotenv_path=ENV_PATH)
# LLM Provider
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "yandexgpt")  # "ollama" или "yandexgpt"
# Ollama
OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434/api/generate")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:7b")

# YandexGPT
YANDEX_API_KEY = os.getenv("YANDEX_API_KEY", "")
YANDEX_FOLDER_ID = os.getenv("YANDEX_FOLDER_ID", "")
YANDEX_MODEL = os.getenv("YANDEX_MODEL", "yandexgpt-lite")

# Timeouts
REQUEST_TIMEOUT = float(os.getenv("REQUEST_TIMEOUT", "60.0"))

def get_active_provider() -> dict:
    """Возвращает информацию об активном провайдере."""
    if LLM_PROVIDER == "ollama":
        return {"provider": "Ollama", "model": OLLAMA_MODEL}
    elif LLM_PROVIDER == "yandexgpt":
        return {"provider": "YandexGPT", "model": YANDEX_MODEL}
    else:
        return {"provider": "Unknown", "model": "Unknown"}