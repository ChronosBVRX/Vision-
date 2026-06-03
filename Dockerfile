# Usar Node.js 20 como imagen base
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

# Directorio de trabajo de la app
WORKDIR /app

# Copiar archivos de dependencias
COPY package*.json ./

# Instalar solo dependencias de producción
RUN npm ci --omit=dev

# Copiar el resto del código del backend
COPY . .

# Puerto por el que escucha Express
EXPOSE 5000

# Arrancar la aplicación
CMD ["node", "server.js"]
