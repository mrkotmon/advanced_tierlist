from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt
from jwt import PyJWTError as JWTError
from config import settings

bearer = HTTPBearer(auto_error=False)

class CurrentUser:
    def __init__(self, user_id: int, username: str):
        self.user_id = user_id
        self.username = username

async def get_current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> CurrentUser:
    if creds is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "No token")
    try:
        payload = jwt.decode(
            creds.credentials, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
        )
    except JWTError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token")
    return CurrentUser(user_id=int(payload["sub"]), username=payload["username"])

def decode_jwt(token: str) -> "CurrentUser | None":
    try:
        payload = jwt.decode(
            token, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
        )
        return CurrentUser(user_id=int(payload["sub"]), username=payload["username"])
    except JWTError:
        return None