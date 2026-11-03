import json, os, time
from typing import Dict
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, Header, HTTPException
from config import settings
from realtime_auth import ws_resolve_user, resolve_user_from_cookie
from deps import CurrentUser
from db import get_pool

router = APIRouter()
INTERNAL_TOKEN = settings.internal_token
DEV_ALLOW_ANON = settings.dev_allow_anon

# tierlist_id -> { cid: (ws, CurrentUser) }
cursor_conns: Dict[int, Dict[str, tuple[WebSocket, CurrentUser]]] = {}
voice_conns:  Dict[int, Dict[str, tuple[WebSocket, CurrentUser]]] = {}

_activity_last: Dict[int, float] = {}  # user_id -> last DB write ts (throttle)

print(f"[BOOT] realtime.py: DEV_ALLOW_ANON={DEV_ALLOW_ANON!r}")


async def _check_access(tierlist_id: int, user_id: int) -> tuple[bool, bool]:
    """Returns (has_access, is_owner)."""
    pool = get_pool()
    owner_id = await pool.fetchval("SELECT owner_id FROM tierlists WHERE id = $1", tierlist_id)
    if owner_id is None:
        return False, False
    if owner_id == user_id:
        return True, True
    row = await pool.fetchrow(
        "SELECT 1 FROM collab_sessions WHERE tierlist_id = $1 AND user_id = $2",
        tierlist_id, user_id)
    return (row is not None), False


async def _touch_activity(user_id: int, tierlist_id: int, force: bool = False):
    now = time.time()
    if not force and now - _activity_last.get(user_id, 0) < 5:
        return
    _activity_last[user_id] = now
    pool = get_pool()
    try:
        await pool.execute(
            "INSERT INTO user_activity (user_id, tierlist_id, last_ping) "
            "VALUES ($1, $2, NOW()) "
            "ON CONFLICT (user_id) DO UPDATE SET tierlist_id = $2, last_ping = NOW()",
            user_id, tierlist_id)
        await pool.execute(
            "UPDATE users SET last_seen = NOW() WHERE id = $1", user_id)
    except Exception as e:
        print(f"[ACTIVITY] {e}")




async def _close_room(tierlist_id: int):
    """Снести БД-строки и оповестить всех, что хост ушёл."""
    pool = get_pool()
    await pool.execute("DELETE FROM user_activity WHERE tierlist_id = $1", tierlist_id)

    conns = cursor_conns.pop(tierlist_id, {})
    for cid, (ws, _) in list(conns.items()):
        try:
            await ws.send_json({"type": "host_left", "tierlistId": tierlist_id})
            await ws.close()
        except Exception:
            pass

    vconns = voice_conns.pop(tierlist_id, {})
    for cid, (ws, _) in list(vconns.items()):
        try:
            await ws.send_json({"type": "host_left", "tierlistId": tierlist_id})
            await ws.close()
        except Exception:
            pass


# ---------- CURSORS ----------
@router.websocket("/ws/cursors")
async def cursor_ws(
    websocket: WebSocket,
    tierlist_id: int = Query(...),
    ticket: str | None = Query(None),
    token: str | None = Query(None),
):
    user = ws_resolve_user(ticket, token)
    if user is None:
        user = await resolve_user_from_cookie(dict(websocket.cookies))
    if user is None and DEV_ALLOW_ANON:
        user = CurrentUser(user_id=0, username=f"dev_{os.urandom(2).hex()}")

    has_access, is_owner = await _check_access(tierlist_id, user.user_id)
    if DEV_ALLOW_ANON and user.user_id == 0:
        has_access, is_owner = True, False
    if not has_access:
        print(f"[WS] 403 user={user.username} tierlist={tierlist_id} (no access)")
        await websocket.close(code=1008)
        return

    await websocket.accept()
    conns = cursor_conns.setdefault(tierlist_id, {})
    cid = user.username
    if cid in conns:
        cid = f"{cid}_{os.urandom(2).hex()}"
    conns[cid] = (websocket, user)
    await _touch_activity(user.user_id, tierlist_id, force=True)

    print(f"[CURSOR+] {cid} (owner={is_owner}) tierlist={tierlist_id} total={len(conns)}")

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                continue
            payload.update(clientId=cid, username=user.username, tierlistId=tierlist_id)
            await _touch_activity(user.user_id, tierlist_id)

            dead = []
            for other, (ws, _) in list(conns.items()):
                if other == cid:
                    continue
                try:
                    await ws.send_json(payload)
                except Exception:
                    dead.append(other)
            for d in dead:
                conns.pop(d, None)
    except WebSocketDisconnect:
        pass
    finally:
        conns = cursor_conns.get(tierlist_id, {})
        conns.pop(cid, None)

        pool = get_pool()

        # Чистим активность ЭТОГО пользователя
        try:
            await pool.execute(
                "DELETE FROM user_activity WHERE user_id = $1", user.user_id)
        except Exception as e:
            print(f"[ACTIVITY CLEAR] {e}")

        # Хост ушёл?
        owner_id = await pool.fetchval(
            "SELECT owner_id FROM tierlists WHERE id = $1", tierlist_id)
        host_present = any(u.user_id == owner_id for _, u in conns.values()) if owner_id else False

        if owner_id is not None and not host_present:
            print(f"[CURSOR] host left tierlist={tierlist_id} → closing room")
            await _close_room(tierlist_id)
        else:
            for ws, _ in list(conns.values()):
                try:
                    await ws.send_json({"type": "leave", "clientId": cid})
                except Exception:
                    pass
            if not conns:
                cursor_conns.pop(tierlist_id, None)

        print(f"[CURSOR-] {cid} tierlist={tierlist_id} total={len(conns)}")


# ---------- VOICE ----------
@router.websocket("/ws/voice")
async def voice_ws(
    websocket: WebSocket,
    tierlist_id: int = Query(...),
    ticket: str | None = Query(None),
    token: str | None = Query(None),
):
    user = ws_resolve_user(ticket, token)
    if user is None:
        user = await resolve_user_from_cookie(dict(websocket.cookies))
    if user is None and DEV_ALLOW_ANON:
        user = CurrentUser(user_id=0, username=f"dev_{os.urandom(2).hex()}")
    has_access, _ = await _check_access(tierlist_id, user.user_id)
    if not has_access and not (DEV_ALLOW_ANON and user.user_id == 0):
        await websocket.close(code=1008)
        return

    await websocket.accept()
    conns = voice_conns.setdefault(tierlist_id, {})
    cid = user.username
    if cid in conns:
        cid = f"{cid}_{os.urandom(2).hex()}"
    conns[cid] = (websocket, user)

    await websocket.send_json({
        "type": "peers",
        "peers": [{"clientId": c, "name": u.username} for c, (_, u) in conns.items() if c != cid],
    })
    for c, (ws, _) in conns.items():
        if c != cid:
            try:
                await ws.send_json({"type": "peer-joined", "clientId": cid, "name": user.username})
            except Exception:
                pass

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            msg["from"] = cid
            msg["fromName"] = user.username
            target = msg.get("to")
            if target and target in conns:
                await conns[target][0].send_json(msg)
    except WebSocketDisconnect:
        pass
    finally:
        conns = voice_conns.get(tierlist_id, {})
        conns.pop(cid, None)
        for ws, _ in list(conns.values()):
            try:
                await ws.send_json({"type": "peer-left", "clientId": cid})
            except Exception:
                pass
        if not conns:
            voice_conns.pop(tierlist_id, None)


# ---------- Внутренний broadcast (Java → Python) ----------
@router.post("/internal/broadcast")
async def internal_broadcast(payload: dict, x_internal_token: str | None = Header(None)):
    if INTERNAL_TOKEN and x_internal_token != INTERNAL_TOKEN:
        raise HTTPException(401, "invalid internal token")
    tierlist_id = int(payload.get("tierlistId", 0))
    msg = {"type": "tierlist_update", "tierlistId": tierlist_id, **payload.get("data", {})}
    conns = cursor_conns.get(tierlist_id, {})
    dead = []
    for cid, (ws, _) in list(conns.items()):
        try:
            await ws.send_json(msg)
        except Exception:
            dead.append(cid)
    for d in dead:
        conns.pop(d, None)
    return {"ok": True, "sent": len(conns)}