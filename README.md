# Tier Tool — Tier List Builder with Real-Time Collaboration

Веб-приложение для создания тир-листов с совместным редактированием, отслеживанием присутствия друзей и голосовым чатом.

---

## ✨ Возможности

### Тир-листы
- Создание тир-листов с произвольными тирами (S, A, B, C, …)
- Настройка названий и цветов тиров
- Drag-and-drop объектов между тирами
- Отображение произвольного атрибута поверх картинки (название, год, жанр)
- Контекстное меню (ПКМ) на карточках

### Пулы объектов
- Общие пулы, доступные в нескольких тир-листах
- Произвольный JSON-набор атрибутов у каждого объекта (`JSONB`)
- Загрузка картинок на сервер
- Привязка нескольких пулов к одному тир-листу

### Пользователи и друзья
- Регистрация и вход с BCrypt-хешированием паролей
- Страница профиля: смена имени и пароля
- Система друзей: заявки, принятие/отклонение, список
- Онлайн-статус в реальном времени (кто в сети, кто в редакторе)

### Совместное редактирование
- Курсоры других пользователей на том же тир-листе в реальном времени
- Запрос на присоединение к чужому тир-листу с одобрением хоста
- Голосовой чат через WebRTC + DeepFilterNet3 (шумоподавление)
- Автоматическое закрытие сессии при выходе хоста

---

## 🏗️ Архитектура
┌──────────────────────────────────────────────────────┐
│ Браузер (клиент) │
│ Thymeleaf-страницы + WebSocket + WebRTC │
└───────┬─────────────────────────────────┬────────────┘
│ HTTP │ WebSocket
▼ ▼
┌──────────────────┐ ┌──────────────────┐
│ Caddy (443) │──── HTTPS ──►│ │
│ reverse proxy │ │ │
└───────┬──────────┘ │ │
▼ ▼ │
┌──────────────────┐ ┌──────────────────┐ │
│ Java Backend │◄─────►│ Python Service │◄─────┘
│ Spring Boot │ HTTP │ FastAPI │
│ :8080 │ │ :8001 │
└────────┬─────────┘ └────────┬─────────┘
└──────────┬───────────────┘
▼
┌──────────────────┐
│ PostgreSQL │
│ :5432 │
└──────────────────┘

text

- **Java (Spring Boot)** — бизнес-логика: тир-листы, пулы, объекты, аутентификация, друзья
- **Python (FastAPI)** — real-time слой: WebSocket-курсоры, WebRTC-сигналинг, broadcast-события
- **PostgreSQL** — общая БД для обоих сервисов
- **Caddy** — HTTPS-терминация и path-роутинг

---

## 🛠️ Технологический стек

| Слой | Технологии |
|---|---|
| Backend (Java) | Spring Boot 3.3, Spring Web, Spring Data JPA, JDBC Template, Thymeleaf, BCrypt |
| Backend (Python) | FastAPI, Uvicorn, asyncpg, httpx, psycopg2 |
| База данных | PostgreSQL 16 (JSONB для гибких структур) |
| Frontend | Thymeleaf, SortableJS, WebRTC, DeepFilterNet3 |
| Инфраструктура | Docker Compose, Caddy 2 |
| Аутентификация | HttpSession + JSESSIONID cookie |

---

advanced_tierlist/
│
├── tierlist_backend/ # Java Spring Boot — основной бэкенд
│ ├── src/main/java/com/tierlist/
│ │ ├── controller/
│ │ │ ├── AuthController.java # Вход, регистрация, выход, /api/me
│ │ │ ├── HomeController.java # Страница /home
│ │ │ ├── ProfileController.java # Профиль: смена имени/пароля, друзья
│ │ │ ├── TierListController.java # CRUD тир-листов, редактор, move-object
│ │ │ ├── PoolController.java # CRUD пулов и объектов в них
│ │ │ ├── FriendController.java # Заявки в друзья, принятие/отклонение
│ │ │ ├── PresenceController.java # Heartbeat, /api/friends, /api/leave
│ │ │ ├── CollabController.java # Запросы на совместное редактирование
│ │ │ ├── DbStatusController.java # Техническая страница /db-status
│ │ │ └── PoolApiController.java # (опционально) REST API для пулов
│ │ │
│ │ ├── service/
│ │ │ ├── PoolService.java # Работа с пулами и объектами через JdbcTemplate
│ │ │ ├── FriendService.java # Логика дружеских связей
│ │ │ ├── CollabService.java # Очередь join-запросов, сессии доступа
│ │ │ └── PresenceService.java # (in-memory) отслеживание активности
│ │ │
│ │ ├── config/
│ │ │ ├── WebConfig.java # Статика /uploads/**, регистрация interceptor
│ │ │ ├── SecurityConfig.java # Отключение CSRF, разрешение запросов
│ │ │ ├── WebSocketConfig.java # (если используется) STOMP для Java-WS
│ │ │ └── LastSeenInterceptor.java # Обновляет users.last_seen на каждом запросе
│ │ │
│ │ ├── model/
│ │ │ └── User.java # JPA-сущность пользователя
│ │ │
│ │ ├── repository/
│ │ │ └── UserRepository.java # Spring Data JPA для users
│ │ │
│ │ └── TierlistBackend.java # main(), @SpringBootApplication
│ │
│ ├── src/main/resources/
│ │ ├── templates/ # Thymeleaf HTML
│ │ │ ├── login.html
│ │ │ ├── signup.html
│ │ │ ├── home.html
│ │ │ ├── profile.html
│ │ │ ├── tierlist-list.html
│ │ │ ├── tierlist-detail.html
│ │ │ ├── tierlist-editor.html # Редактор с SortableJS
│ │ │ ├── new-tierlist.html
│ │ │ ├── pools-list.html
│ │ │ ├── new-pool.html
│ │ │ ├── pool-objects.html
│ │ │ ├── add-object.html
│ │ │ └── db-status.html
│ │ │
│ │ ├── static/
│ │ │ ├── index.html # Landing page (отдаётся по /)
│ │ │ └── cursors.js # WebSocket-клиент курсоров
│ │ │
│ │ ├── application.properties # Конфигурация Spring Boot
│ │ ├── application-local.properties # Локальные переопределения (не в git)
│ │ └── data.sql # Схема БД + миграции + seed-данные
│ │
│ ├── uploads/ # Загруженные картинки (не в git)
│ ├── target/ # Maven build (не в git)
│ ├── Dockerfile # Сборка образа Java
│ └── pom.xml # Зависимости Maven
│
├── backend-python/ # Python FastAPI — real-time сервис
│ ├── main.py # FastAPI app, lifespan, static mount
│ ├── realtime.py # WebSocket /ws/cursors и /ws/voice
│ ├── realtime_auth.py # Аутентификация WS через cookie JSESSIONID
│ ├── db.py # asyncpg pool (инициализация/закрытие)
│ ├── deps.py # CurrentUser, JWT-декодер, get_current_user
│ ├── config.py # Pydantic Settings (env vars)
│ ├── routers/
│ │ └── tierlists.py # REST /api/tierlists/{id}
│ ├── static/
│ │ ├── index.html # Страница с курсорами и голосом
│ │ └── cursors.js # (если отдаётся Python'ом, дубль)
│ ├── requirements.txt # Зависимости Python
│ ├── Dockerfile # Сборка образа Python
│ └── .env # Локальные переменные (не в git)
│
├── Caddyfile # HTTPS + reverse proxy
├── docker-compose.yml # Оркестрация java + python + postgres + caddy
├── .env # Секреты (не в git)
├── .gitignore
└── README.md # Этот файл

text

---

## 🚀 Быстрый старт (Docker)

### Требования
- Docker Desktop
- Git

### 1. Клонирование

```bash
git clone <repo-url>
cd advanced_tierlist
2. Переменные окружения
Создай .env в корне:

env
POSTGRES_PASSWORD=changeme
JAVA_API=http://java:8080
DATABASE_URL=postgresql://postgres:changeme@postgres:5432/tierlistdb
INTERNAL_TOKEN=random-secret-token
DEV_ALLOW_ANON=0
3. Запуск
bash
docker compose up -d --build
4. Проверка
Сайт: https://26.66.74.232 (или IP из Caddyfile)

Java API: https://26.66.74.232/api/me

Python health: https://26.66.74.232/realtime/health

Первый вход: admin / admin — сразу смени пароль через /profile.

5. Установка CA-сертификата Caddy
Caddy использует self-signed сертификат (tls internal):

bash
docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt > root.crt
Импорт в Windows: Win+R → certmgr.msc → Доверенные корневые центры сертификации → Импорт → выбрать root.crt.

💻 Локальная разработка (без Docker)
Java Backend
bash
cd tierlist_backend
mvn clean package
java -jar target/backend-1.0-SNAPSHOT.jar
Сервер: http://localhost:8080.
Требуется PostgreSQL на localhost:5432, база tierlistdb.

Python Service
bash
cd backend-python
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python main.py
Сервер: http://localhost:8001.

.env для локальной разработки
env
DATABASE_URL=postgresql://postgres:changeme@localhost:5432/tierlistdb
JAVA_API=http://localhost:8080
INTERNAL_TOKEN=random-secret-token
DEV_ALLOW_ANON=1
DEV_ALLOW_ANON=1 — для локальной разработки без аутентификации.

🔧 Конфигурация
application.properties (Java)
properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://postgres:5432/tierlistdb
spring.datasource.username=postgres
spring.datasource.password=${POSTGRES_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# SQL init
spring.sql.init.mode=always
spring.jpa.defer-datasource-initialization=true
spring.jpa.hibernate.ddl-auto=update

# Uploads
file.upload-dir=/app/uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Sessions
server.servlet.session.cookie.http-only=false
Caddyfile
caddy
{
    default_sni 26.66.74.232
}

https://26.66.74.232 {
    tls internal

    @realtime path /realtime /realtime/* /cursors.js
    reverse_proxy @realtime python:8001

    @ws path /ws/*
    reverse_proxy @ws python:8001

    @pyapi path /api/tierlists/*
    reverse_proxy @pyapi python:8001

    reverse_proxy java:8080
}
📊 Схема базы данных
Таблица	Назначение
users	Пользователи: username, password, last_seen
pools	Пулы объектов (Игры, Фильмы, Аниме)
objects	Объекты: name, image, status, attributes JSONB
tierlists	Тир-листы: name, tierlist_data JSONB, owner_id
collab_sessions	Доступ гостей к чужим тир-листам
user_activity	Кто сейчас в каком редакторе (heartbeat)
friendships	Дружеские связи (двусторонние)
friend_requests	Заявки в друзья со статусом
Структура tierlist_data (JSONB):

json
{
  "pools": ["Игры", "Фильмы"],
  "displayAttribute": "название",
  "tiers": {
    "S": [{"objectId": 1, "position": 0}, {"objectId": 5, "position": 1}],
    "A": [],
    "B": [],
    "unassigned": [{"objectId": 8, "position": 0}]
  },
  "tierColors": {"S": "#FF9B9B", "A": "#FFDE6B"}
}
🌐 Основные эндпоинты
Java (:8080)
Метод	Путь	Назначение
GET	/	Landing page
GET/POST	/login, /signup	Аутентификация
GET	/home	Домашняя страница
GET	/profile	Профиль
GET	/tierlist/list	Список тир-листов
GET	/tierlist/{id}/edit	Редактор
POST	/tierlist/{id}/move-object	Переместить объект
POST	/tierlist/{id}/tiers/add	Добавить тир
POST	/tierlist/{id}/tiers/remove	Удалить тир
POST	/tierlist/{id}/display-attribute	Сменить отображаемый атрибут
POST	/api/heartbeat	Запись user_activity
GET	/api/friends	Друзья с presence
POST	/api/collab/request	Запрос на присоединение
POST	/api/collab/accept	Принять запрос
GET	/api/collab/pending	Входящие запросы
GET	/api/collab/waiting	Гость опрашивает accept
GET	/api/me	Текущий пользователь
Python (:8001)
Метод	Путь	Назначение
WS	/ws/cursors?tierlist_id=N	Курсоры
WS	/ws/voice?tierlist_id=N	WebRTC-сигналинг
POST	/internal/broadcast	Java → Python broadcast
GET	/health	Healthcheck
🗺️ Roadmap
☑ Базовый CRUD тир-листов и пулов
☑ Пользователи, друзья, presence
☑ Синхронизация курсоров
□ Атрибуты объектов (JSONB)
□ Drag-and-drop в редакторе
□ Синхронизация изменений тир-листа через WebSocket
□ Блокировка объектов при перетаскивании (lock/unlock)
□ Экспорт тир-листа в PNG
🐛 Известные ограничения
Self-signed HTTPS — браузер потребует установки CA (см. «Быстрый старт»)

DEV_ALLOW_ANON=1 — не для продакшена

Без rate limiting — при публичном развёртывании надо защиту от перебора на /login

🗝️ .gitignore
gitignore
.env
*.pem
*.crt
uploads/
target/
.venv/
.idea/
*.iml
__pycache__/
*.pyc
text
