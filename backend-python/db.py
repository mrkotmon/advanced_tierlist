import asyncpg, json
from config import settings

pool: asyncpg.Pool | None = None

async def init_pool():
    global pool
    pool = await asyncpg.create_pool(settings.database_url, min_size=2, max_size=10)

async def close_pool():
    if pool:
        await pool.close()

def get_pool() -> asyncpg.Pool:
    assert pool is not None
    return pool