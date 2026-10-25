-- Порядок важен: users первая (на неё ссылаются friend_requests и friendships)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    last_seen TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS pools (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO pools (name) VALUES ('Игры') ON CONFLICT (name) DO NOTHING;
INSERT INTO pools (name) VALUES ('Фильмы') ON CONFLICT (name) DO NOTHING;
INSERT INTO pools (name) VALUES ('Аниме') ON CONFLICT (name) DO NOTHING;

CREATE TABLE IF NOT EXISTS objects (
    id SERIAL PRIMARY KEY,
    pool_id INTEGER REFERENCES pools(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'free',
    image VARCHAR(500),
    attributes JSONB DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS tierlists (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tierlist_data JSONB DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS friend_requests (
    id SERIAL PRIMARY KEY,
    from_user_id INTEGER NOT NULL REFERENCES users(id),
    to_user_id INTEGER NOT NULL REFERENCES users(id),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS friendships (
    id SERIAL PRIMARY KEY,
    user_id1 INTEGER NOT NULL REFERENCES users(id),
    user_id2 INTEGER NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);