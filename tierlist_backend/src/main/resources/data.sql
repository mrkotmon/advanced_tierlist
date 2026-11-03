-- ============================================================
-- 1. USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    last_seen TIMESTAMP DEFAULT NOW()
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP DEFAULT NOW();

INSERT INTO users (username, password) VALUES ('admin', 'admin')
    ON CONFLICT (username) DO NOTHING;

-- ============================================================
-- 2. POOLS
-- ============================================================
CREATE TABLE IF NOT EXISTS pools (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO pools (name) VALUES ('Игры') ON CONFLICT (name) DO NOTHING;
INSERT INTO pools (name) VALUES ('Фильмы') ON CONFLICT (name) DO NOTHING;
INSERT INTO pools (name) VALUES ('Аниме') ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 3. OBJECTS
-- ============================================================
CREATE TABLE IF NOT EXISTS objects (
    id SERIAL PRIMARY KEY,
    pool_id INTEGER REFERENCES pools(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'free',
    image VARCHAR(500),
    attributes JSONB DEFAULT '{}'
);

-- ============================================================
-- 4. TIERLISTS
-- ============================================================
CREATE TABLE IF NOT EXISTS tierlists (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tierlist_data JSONB DEFAULT '{}'
);

-- 4.1 Колонка owner_id (миграция для старых БД)
ALTER TABLE tierlists ADD COLUMN IF NOT EXISTS owner_id INTEGER;

-- 4.2 Заполняем старые записи (владелец = admin)
UPDATE tierlists
SET owner_id = (SELECT id FROM users WHERE username = 'admin' LIMIT 1)
WHERE owner_id IS NULL;

-- 4.3 NOT NULL (если NULL уже нет — просто no-op, если есть — упадёт, но у нас UPDATE выше)
ALTER TABLE tierlists ALTER COLUMN owner_id SET NOT NULL;

-- 4.4 Внешний ключ — дропаем и создаём заново (идемпотентно)
ALTER TABLE tierlists DROP CONSTRAINT IF EXISTS tierlists_owner_id_fkey;
ALTER TABLE tierlists
    ADD CONSTRAINT tierlists_owner_id_fkey
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;

-- 4.5 Индекс
CREATE INDEX IF NOT EXISTS idx_tierlists_owner ON tierlists(owner_id);

-- ============================================================
-- 5. FRIEND REQUESTS
-- ============================================================
CREATE TABLE IF NOT EXISTS friend_requests (
    id SERIAL PRIMARY KEY,
    from_user_id INTEGER NOT NULL REFERENCES users(id),
    to_user_id INTEGER NOT NULL REFERENCES users(id),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- 6. FRIENDSHIPS
-- ============================================================
CREATE TABLE IF NOT EXISTS friendships (
    id SERIAL PRIMARY KEY,
    user_id1 INTEGER NOT NULL REFERENCES users(id),
    user_id2 INTEGER NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- 7. COLLAB SESSIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS collab_sessions (
    id SERIAL PRIMARY KEY,
    tierlist_id INTEGER NOT NULL REFERENCES tierlists(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    granted_at TIMESTAMP DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_collab_tierlist_user
    ON collab_sessions(tierlist_id, user_id);

-- ============================================================
-- 8. USER ACTIVITY
-- ============================================================
CREATE TABLE IF NOT EXISTS user_activity (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    tierlist_id INTEGER REFERENCES tierlists(id) ON DELETE SET NULL,
    last_ping TIMESTAMP DEFAULT NOW()
);