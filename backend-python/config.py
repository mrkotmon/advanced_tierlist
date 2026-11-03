from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    database_url: str
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    internal_token: str = ""
    dev_allow_anon: bool = False
    java_api: str = "http://java:8080"   # ← добавь это

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")     # bool сам распарсит "0"/"1"/"true"

settings = Settings()