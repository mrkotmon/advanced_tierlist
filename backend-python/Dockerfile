FROM python:3.12-slim

WORKDIR /app

# Системные зависимости — один слой, кэшируется навсегда
RUN apt-get update && apt-get install -y --no-install-recommends \
        gcc libpq-dev \
 && rm -rf /var/lib/apt/lists/*

# Зависимости Python — cache mount + официальный PyPI
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt \
        --index-url https://pypi.org/simple

# Код
COPY . .

# Non-root
RUN groupadd -r app && useradd -r -g app app \
 && chown -R app:app /app
USER app

EXPOSE 8001
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]