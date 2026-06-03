# Instrucciones de Despliegue para el Agente (Docker Local)

> **Para el Agente Inteligente:** Lee este archivo completamente antes de empezar. Tu objetivo es automatizar la instalación de dependencias, la configuración del entorno y la puesta en marcha de **Vision+** mediante Docker en esta nueva máquina. Realiza todo lo que puedas mediante comandos y, si te encuentras con un bloqueo que requiere intervención física del usuario (como abrir un programa con interfaz gráfica por primera vez), notifícalo detalladamente.

---

## 🛠️ Paso 1: Instalar dependencias del sistema usando `winget` (Windows)

Dado que estás en un entorno Windows, puedes intentar instalar Git y Docker Desktop utilizando el gestor de paquetes de Windows (`winget`) de forma desatendida. Ejecuta los siguientes comandos en PowerShell:

### 1.1 Instalar Git:
```powershell
winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements
```

### 1.2 Instalar Docker Desktop:
```powershell
winget install --id Docker.DockerDesktop -e --silent --accept-source-agreements --accept-package-agreements
```
> *Nota: Después de instalar Docker Desktop, es posible que el usuario necesite reiniciar el equipo para activar WSL2 en la BIOS o características de virtualización de Windows. Si la instalación de Docker requiere reinicio, pídele al usuario que lo haga y que vuelva a abrir la terminal.*

---

## 🚀 Paso 2: Verificar que Docker esté activo

Una vez instalado Docker, verifica si el servicio/demonio de Docker está corriendo en segundo plano:
```powershell
docker info
```
* **Si el comando falla o dice que no puede conectarse al demonio:** Pídele al usuario que **abra manualmente el programa "Docker Desktop"** desde el menú de inicio de Windows y que acepte los términos de servicio iniciales. Vuelve a comprobar con `docker info` hasta que responda con éxito.

---

## 📝 Paso 3: Crear el archivo `.env` en la raíz del proyecto

Crea un archivo llamado `.env` en el directorio raíz de `Vision+` con el siguiente contenido de producción:

```env
PORT=5000
AUTHORIZED_VIDEO_DOMAINS=mi-dominio.com,mi-cdn.com,proveedor-autorizado.com,mp4movies.us,cuevana.ac,voe.sx,streamwish.to,filemoon.to,poseidonhd.co,poseidonhd2.co,streamtape.com,doodstream.com,upstream.to,mixdrop.co,fembed.com,vidcloud9.com,rabbitstream.net,embed.su,vidmoly.to,vidhide.com,voemkv.xyz,voe.bar

PLUTO_COUNTRY=MX
PLUTO_REGION=mx
PLUTO_ALLOW_MX_FALLBACK=false
PLUTO_USE_HLS_PROXY=true
PLUTO_VALIDATE_STREAMS=true
```

---

## 📂 Paso 4: Configurar la Base de Datos (SQLite)

1. Comprueba si existe la carpeta `data/` en la raíz del proyecto. Si no existe, créala.
2. Pregúntale al usuario si tiene un respaldo físico del archivo `database.sqlite` (o la carpeta `data/`) de su otra PC que quiera usar.
   * **Si sí:** Indícale la ruta exacta de la carpeta `data/` del proyecto para que pegue su archivo ahí.
   * **Si no:** No te preocupes, la aplicación creará automáticamente una base de datos nueva e importará los canales y configuraciones base en el primer inicio.

---

## 🏗️ Paso 5: Compilar y levantar la aplicación con Docker Compose

Ejecuta el siguiente comando en la raíz del proyecto para descargar las imágenes base, compilar el frontend React y arrancar el backend en segundo plano:

```powershell
docker compose up -d --build
```

### 5.1 Verificar que los contenedores estén corriendo:
```powershell
docker ps
```
Deberías ver el contenedor `vision-plus` activo y escuchando en el puerto `0.0.0.0:5000->5000/tcp`.

---

## 🌐 Paso 6: Configurar acceso externo con Cloudflare Tunnels (Opcional)

Si el usuario quiere compartir la app con sus amigos de forma gratuita y segura:

### 6.1 Instalar cloudflared:
```powershell
winget install --id Cloudflare.cloudflared -e --silent --accept-source-agreements --accept-package-agreements
```

### 6.2 Iniciar el túnel rápido de prueba:
Ejecuta el comando en una terminal persistente o en segundo plano:
```powershell
cloudflared tunnel --url http://localhost:5000
```
Lee la salida del comando, busca la dirección pública `https://*.trycloudflare.com` generada y dásela al usuario para que pueda acceder a su aplicación desde cualquier lugar del mundo.
