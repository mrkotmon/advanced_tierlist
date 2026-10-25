import json, os
from typing import Dict
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, Header, HTTPException
from config import settings
from realtime_auth import ws_resolve_user
from deps import CurrentUser

router = APIRouter()

# tierlist_id -> { client_id: (ws, username) }
cursor_conns: Dict[int, Dict[str, tuple[WebSocket, str]]] = {}
voice_conns:  Dict[int, Dict[str, tuple[WebSocket, str]]] = {}

INTERNAL_TOKEN = settings.internal_token
DEV_ALLOW_ANON = settings.dev_allow_anon
print(f"[BOOT] realtime.py loaded: file={__file__}, DEV_ALLOW_ANON={DEV_ALLOW_ANON!r}")


def _resolve(ws_user: CurrentUser | None, tierlist_id: int) -> tuple[str, CurrentUser]:
    """Возвращает (client_id, user). client_id уникален внутри тирлиста."""
    if ws_user is None:
        if not DEV_ALLOW_ANON:
            return None, None
        ws_user = CurrentUser(user_id=0, username=f"dev_{os.urandom(2).hex()}")
    cid = ws_user.username
    if tierlist_id in cursor_conns and cid in cursor_conns[tierlist_id]:
        cid = f"{ws_user.username}_{os.urandom(2).hex()}"
    return cid, ws_user


# ---------- CURSORS ----------
@router.websocket("/ws/cursors")
async def cursor_ws(
    websocket: WebSocket,
    tierlist_id: int = Query(...),
    ticket: str | None = Query(None),
    token: str | None = Query(None),
):
    user = ws_resolve_user(ticket, token)
    print(
        f"[WS] ENTER: tierlist_id={tierlist_id}, ticket={ticket!r}, token={'yes' if token else 'no'}, DEV_ALLOW_ANON={DEV_ALLOW_ANON!r}")
    cid, user = _resolve(user, tierlist_id)
    print(f"[WS] after _resolve: cid={cid!r}, user={user!r}")
    if user is None:
        await websocket.close(code=1008)
        return

    await websocket.accept()
    conns = cursor_conns.setdefault(tierlist_id, {})
    conns[cid] = (websocket, user.username)

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                continue
            payload.update(clientId=cid, username=user.username, tierlistId=tierlist_id)

            dead = []
            for other_id, (ws, _) in list(conns.items()):
                if other_id == cid:
                    continue
                try:
                    await ws.send_json(payload)
                except Exception:
                    dead.append(other_id)
            for d in dead:
                conns.pop(d, None)
    except WebSocketDisconnect:
        pass
    finally:
        conns.pop(cid, None)
        if not conns:
            cursor_conns.pop(tierlist_id, None)
        for ws, _ in conns.values():
            try:
                await ws.send_json({"type": "leave", "clientId": cid})
            except Exception:
                pass


# ---------- VOICE (WebRTC signaling) ----------
@router.websocket("/ws/voice")
async def voice_ws(
    websocket: WebSocket,
    tierlist_id: int = Query(...),
    ticket: str | None = Query(None),
    token: str | None = Query(None),
):
    user = ws_resolve_user(ticket, token)
    cid, user = _resolve(user, tierlist_id)
    if user is None:
        await websocket.close(code=1008)
        return

    await websocket.accept()
    conns = voice_conns.setdefault(tierlist_id, {})
    conns[cid] = (websocket, user.username)

    # 1. Новичку — список присутствующих
    await websocket.send_json({
        "type": "peers",
        "peers": [{"clientId": c, "name": n} for c, (_, n) in conns.items() if c != cid],
    })
    # 2. Остальным — что пришёл новый
    for c, (ws, _) in conns.items():
        if c == cid:
            continue
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
        conns.pop(cid, None)
        if not conns:
            voice_conns.pop(tierlist_id, None)
        for ws, _ in conns.values():
            try:
                await ws.send_json({"type": "peer-left", "clientId": cid})
            except Exception:
                pass


# ---------- Внутренний broadcast (Java → Python) ----------
@router.post("/internal/broadcast")
async def internal_broadcast(
    payload: dict,
    x_internal_token: str | None = Header(None),
):
    if not INTERNAL_TOKEN or x_internal_token != INTERNAL_TOKEN:
        raise HTTPException(401, "invalid internal token")

    tierlist_id = int(payload.get("tierlistId", 0))
    message = {"type": "tierlist_update", "tierlistId": tierlist_id, **payload.get("data", {})}

    conns = cursor_conns.get(tierlist_id, {})
    dead = []
    for cid, (ws, _) in list(conns.items()):
        try:
            await ws.send_json(message)
        except Exception:
            dead.append(cid)
    for d in dead:
        conns.pop(d, None)
    return {"ok": True, "sent": len(conns) - len(dead)}