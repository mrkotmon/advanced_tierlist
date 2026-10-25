import json
from fastapi import APIRouter, Depends, HTTPException
from db import get_pool
from deps import get_current_user, CurrentUser

router = APIRouter()

@router.get("/{tierlist_id}")
async def get_tierlist(
    tierlist_id: int,
    user: CurrentUser = Depends(get_current_user),
):
    pool = get_pool()
    row = await pool.fetchrow(
        "SELECT id, name, tierlist_data FROM tierlists WHERE id = $1",
        tierlist_id,
    )
    if row is None:
        raise HTTPException(404, "Tierlist not found")
    # asyncpg отдаёт JSONB как строку — распарсим
    data = row["tierlist_data"]
    if isinstance(data, str):
        data = json.loads(data)
    return {"id": row["id"], "name": row["name"], "data": data}