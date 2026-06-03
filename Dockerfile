# 1. Etapa de compilación del frontend React
FROM node:20-slim AS frontend-builder
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Etapa final del servidor Express + Chromium
FROM node:20-slim

# Instalar dependencias necesarias para Chromium y Puppeteer/Playwright
RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    ca-certificates \
    procps \
    libxss1 \
    libasound2 \
    libatk1.0-0 \
    libc6 \
    libcairo2 \
    libcups2 \
    libdbus-1-3 \
    libexpat1 \
    libfontconfig1 \
    libgbm1 \
    libgcc1 \
    libgconf-2-4 \
    libgdk-pixbuf2.0-0 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libstdc++6 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxtst6 \
    fonts-liberation \
    libnss3 \
    chromium \
    --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

# Configurar variables de entorno para que Puppeteer use el Chromium instalado por apt-get
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /app

# Copiar dependencias del backend e instalar dependencias de producción
COPY package*.json ./
RUN npm ci --omit=dev

# Copiar el backend completo
COPY . .

# Copiar el frontend compilado en la etapa 1 a la ruta correcta para Express
COPY --from=frontend-builder /frontend/dist ./frontend/dist

# Exponer el puerto de Express (5000 por defecto en .env)
EXPOSE 5000

# Arrancar la aplicación
CMD ["node", "server.js"]

