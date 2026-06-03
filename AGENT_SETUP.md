# Instrucciones de Despliegue para el Agente (Nativo en Windows)

> **Para el Agente Inteligente:** Lee este archivo completamente antes de empezar. Tu objetivo es automatizar la instalación de dependencias, configuración de variables de entorno, compilación del frontend y puesta en marcha de **Vision+** de forma nativa en este sistema Windows. Realiza todo lo que puedas mediante comandos y, si necesitas intervención física del usuario (como abrir un instalador con interfaz gráfica o reiniciar la consola), notifícalo detalladamente.

---

## 🛠️ Paso 1: Instalar dependencias del sistema usando `winget`

Ejecuta los siguientes comandos en PowerShell de forma desatendida para instalar las herramientas requeridas:

### 1.1 Instalar Git:
```powershell
winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements
```

### 1.2 Instalar Node.js (LTS):
```powershell
winget install --id OpenJS.NodeJS.LTS -e --silent --accept-source-agreements --accept-package-agreements
```

> ⚠️ **IMPORTANTE:** Después de instalar Node.js y Git, debes pedirle al usuario que **cierre y vuelva a abrir su terminal**. De lo contrario, los comandos `node` y `npm` no serán reconocidos en la sesión actual.

---

## 🚀 Paso 2: Descargar el código y configurar el entorno

### 2.1 Descargar el código (si el usuario no lo ha hecho ya):
Si no estás en la carpeta del repositorio clonado, clónalo:
```powershell
git clone https://github.com/ChronosBVRX/Vision-.git
cd Vision-
```

### 2.2 Crear el archivo `.env` en la raíz:
Crea un archivo llamado `.env` en la raíz del proyecto con la siguiente configuración:

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

## 📂 Paso 3: Configurar la Base de Datos (SQLite)

1. Comprueba si existe la carpeta `data/` en la raíz del proyecto. Si no existe, créala.
2. Pregúntale al usuario si tiene un respaldo físico del archivo `database.sqlite` (o la carpeta `data/`) de su otra PC que quiera usar.
   * **Si sí:** Indícale que lo copie y pegue en la carpeta `data/` del proyecto.
   * **Si no:** La aplicación creará automáticamente una base de datos SQLite limpia al iniciar por primera vez.

---

## 📦 Paso 4: Instalar dependencias del proyecto

Ejecuta la instalación de dependencias tanto del backend como del frontend:

```powershell
# Instalar dependencias del Backend (raíz)
npm install

# Instalar dependencias del Frontend
npm install --prefix frontend
```

---

## 🏗️ Paso 5: Compilar el Frontend para Producción

Para que la aplicación consuma la menor cantidad de recursos (RAM y CPU) en la PC servidor, compila el frontend de React para que Express lo sirva de forma estática en el puerto 5000:

```powershell
npm run build --prefix frontend
```

---

## 🏁 Paso 6: Arrancar el Servidor

Arranca la aplicación en modo producción:

```powershell
npm start
```
La aplicación estará en línea y disponible en:
👉 **`http://localhost:5000`**

*(Nota: Si el usuario prefiere iniciar la app en modo desarrollo para debuggear con autoreload, puede ejecutar el comando `npm run dev` o hacer doble clic en el archivo `start.bat` de la raíz).*

---

## 🔄 Paso 7: Configurar Inicio Automático con Windows (Opcional)

Si el usuario quiere que la aplicación se inicie sola al encender la PC (ideal para un servidor doméstico):

1. Indícale que presione `Win + R`, escriba `shell:startup` y presione Enter. Esto abrirá la carpeta de Inicio de Windows.
2. Pídele que cree un **Acceso Directo** (Shortcut) al archivo `start.bat` del proyecto y lo guarde dentro de esa carpeta.

---

## 🌐 Paso 8: Configurar acceso externo con Cloudflare Tunnels (Opcional)

Para compartir la app de forma gratuita con sus 10 amigos sin abrir puertos en el router:

### 8.1 Instalar cloudflared:
```powershell
winget install --id Cloudflare.cloudflared -e --silent --accept-source-agreements --accept-package-agreements
```

### 8.2 Levantar el túnel rápido de prueba:
Ejecuta en una terminal secundaria o proceso persistente:
```powershell
cloudflared tunnel --url http://localhost:5000
```
Proporciónale al usuario la URL pública `https://*.trycloudflare.com` autogenerada que aparezca en la consola.
