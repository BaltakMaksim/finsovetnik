from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api import routes
from app.config.config import get_active_provider
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

app = FastAPI(
    title="ФинСоветник AI Service",
    description="AI-сервис для семейного финансового консультанта",
    version="1.0.0"
)

# CORS (чтобы React мог обращаться)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # В продакшене укажите конкретные домены
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Подключаем роутер
app.include_router(routes.router, prefix="/api")

@app.get("/")
async def root():
    return {
        "service": "ФинСоветник AI Service",
        "status": "running",
        "version": "1.0.0",
        "provider": get_active_provider()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)