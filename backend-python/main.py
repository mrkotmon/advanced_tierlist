from pathlib import Path
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from db import init_pool, close_pool
from routers import tierlists
from realtime import router as realtime_router   # когда дойдёшь до WS


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_pool()
    yield
    await close_pool()


app = FastAPI(lifespan=lifespan, title="Tierlist Service")

app.include_router(tierlists.router, prefix="/api/tierlists", tags=["tierlists"])
app.include_router(realtime_router)

# Статика — В САМОМ КОНЦЕ
STATIC_DIR = Path(__file__).parent / "static"
app.mount("/realtime", StaticFiles(directory=STATIC_DIR, html=True), name="realtime")
app.mount("/", StaticFiles(directory=STATIC_DIR, html=True), name="static")


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8001,
        reload=True,
        ws_ping_interval=120,
        ws_ping_timeout=120
    )