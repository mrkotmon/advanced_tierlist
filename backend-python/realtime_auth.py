import secrets, time
from typing import Dict

import httpx
from fastapi import APIRouter, Depends, HTTPException

from config import settings
from deps import get_current_user, CurrentUser, decode_jwt

router = APIRouter()

# ticket -> (user_id, username, expires_at)
_tickets: Dict[str, tuple[int, str, float]] = {}
TICKET_TTL = 60.0

@router.post("/api/ws-ticket")
async def create_ws_ticket(user: CurrentUser = Depends(get_current_user)):
    ticket = secrets.token_urlsafe(32)
    _tickets[ticket] = (user.user_id, user.username, time.time() + TICKET_TTL)
    return {"ticket": ticket, "expiresIn": TICKET_TTL}

def consume_ticket(ticket: str) -> CurrentUser | None:
    entry = _tickets.pop(ticket, None)  # pop = one-time use
    if entry is None:
        return None
    user_id, username, expires = entry
    if time.time() > expires:
        return None
    return CurrentUser(user_id, username)

def ws_resolve_user(ticket: str | None, token: str | None) -> CurrentUser | None:
    """Пробует ticket (prod) или JWT в query (fallback для дебага)."""
    if ticket:
        return consume_ticket(ticket)
    if token:
        return decode_jwt(token)
    return None

async def resolve_user_from_cookie(cookies: dict) -> "CurrentUser | None":
    jsessionid = cookies.get("JSESSIONID")
    if not jsessionid:
        return None
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            r = await client.get(
                f"{settings.java_api}/api/me",
                cookies={"JSESSIONID": jsessionid},
            )
            if r.status_code == 200:
                data = r.json()
                if data.get("authenticated"):
                    return CurrentUser(
                        user_id=int(data["userId"]),
                        username=data["username"],
                    )
    except Exception as e:
        print(f"[AUTH] {e}")
    return None