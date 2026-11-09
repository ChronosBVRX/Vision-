"use strict";

/**
 * Vision+ PC — ejecutable portable único (Electron).
 *
 * Un solo .exe contiene:
 *   - Chromium/Electron (ventana nativa, sin depender de Edge/Chrome)
 *   - la app de Smart TV (app/)
 *   - el motor Node del port webOS (service/)
 *   - el servidor local (server.js)
 *   - FFmpeg Windows (runtime/ffmpeg.exe)
 *
 * Los datos (sesión, caché, localStorage y perfil) viven junto al .exe en
 * "VisionPlusPC-data/" cuando se ejecuta el portable de electron-builder.
 */

const { app, BrowserWindow, Menu, Tray, nativeImage, dialog, shell } = require("electron");
const updater = require("./updater");
const fs = require("fs");
const http = require("http");
const path = require("path");

const PREFERRED_PORT = 8910;
const APP_NAME = "Vision+ PC";

let mainWindow = null;
let splashWindow = null;
let tray = null;
let quitting = false;
let currentPort = 0;
let dataDir = "";
let allowFullscreenExit = false;
let serverStarted = false;
let serverRuntime = null;

/* ------------------------------------------------------------------ */
/* Rutas portables                                                     */
/* ------------------------------------------------------------------ */

function ensureDir(dir) {
  try {
    fs.mkdirSync(dir, { recursive: true });
    return true;
  } catch {
    return false;
  }
}

function portableBase() {
  if (!app.isPackaged) return app.getPath("userData");
  return process.env.PORTABLE_EXECUTABLE_DIR || path.dirname(process.execPath);
}

function prepareDataDir() {
  const preferred = path.join(portableBase(), "VisionPlusPC-data");
  if (ensureDir(preferred)) {
    dataDir = preferred;
  } else {
    dataDir = path.join(app.getPath("userData"), "data");
    ensureDir(dataDir);
  }

  // El perfil de Chromium (localStorage, cookies, caché) también va junto al .exe.
  const electronData = path.join(dataDir, "electron");
  if (ensureDir(electronData)) {
    try {
      app.setPath("userData", electronData);
      app.setPath("sessionData", electronData);
    } catch {
      /* si falla, Electron usa su ruta normal */
    }
  }
}

function log(message) {
  try {
    const base = dataDir || app.getPath("userData");
    ensureDir(base);
    fs.appendFileSync(
      path.join(base, "launcher.log"),
      new Date().toISOString() + " " + message + "\n",
    );
  } catch {
    /* noop */
  }
}

/* ------------------------------------------------------------------ */
/* Servidor local                                                      */
/* ------------------------------------------------------------------ */

function startServer() {
  if (serverStarted) return Promise.resolve(currentPort);
  serverStarted = true;

  const appDir = path.join(__dirname, "app");
  const serviceDir = path.join(__dirname, "service");
  const ffmpeg = path.join(__dirname, "runtime", "ffmpeg.exe");
  const serverScript = path.join(__dirname, "server.js");

  for (const required of [appDir, serviceDir, serverScript, path.join(serviceDir, "core.js"), ffmpeg]) {
    if (!fs.existsSync(required)) {
      return Promise.reject(new Error("Falta un componente de Vision+ PC: " + required));
    }
  }

  process.env.VISION_PC = "1";
  process.env.VISION_APP_DIR = appDir;
  process.env.VISION_SERVICE_DIR = serviceDir;
  process.env.VISION_DATA_DIR = dataDir;
  process.env.VISION_FFMPEG = ffmpeg;
  process.env.VISION_PORT = String(PREFERRED_PORT);
  process.env.PORT = String(PREFERRED_PORT);
  process.env.VISION_SHUTDOWN_TOKEN =
    process.env.VISION_SHUTDOWN_TOKEN || String(Date.now()) + Math.random().toString(16).slice(2);

  const portFile = path.join(dataDir, "port.json");
  try {
    fs.unlinkSync(portFile);
  } catch {
    /* noop */
  }

  serverRuntime = require(serverScript);
  return waitForPort(portFile, 30000);
}

function waitForPort(portFile, timeoutMs) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const timer = setInterval(() => {
      try {
        if (fs.existsSync(portFile)) {
          const info = JSON.parse(fs.readFileSync(portFile, "utf8"));
          const port = Number(info.port);
          if (port > 0) {
            clearInterval(timer);
            currentPort = port;
            log("servidor listo en 127.0.0.1:" + port);
            resolve(port);
            return;
          }
        }
      } catch {
        /* el archivo se escribe de forma atómica; reintentar */
      }

      if (Date.now() - started > timeoutMs) {
        clearInterval(timer);
        reject(new Error("El servidor local no respondió en 30 segundos."));
      }
    }, 120);
  });
}

function ping(port, attemptsLeft) {
  return new Promise((resolve) => {
    const request = http.request(
      { host: "127.0.0.1", port, path: "/ping", method: "POST", headers: { "Content-Type": "application/json" }, timeout: 5000 },
      (response) => {
        response.resume();
        if (response.statusCode === 200) {
          resolve(true);
          return;
        }
        resolve(false);
      },
    );
    request.on("error", () => resolve(false));
    request.on("timeout", () => {
      request.destroy();
      resolve(false);
    });
    request.end("{}");
  }).then((ok) => {
    if (ok || !(attemptsLeft > 1)) return ok;
    return new Promise((resolve) => setTimeout(resolve, 350)).then(() => ping(port, attemptsLeft - 1));
  });
}

/* ------------------------------------------------------------------ */
/* Pantalla de carga                                                   */
/* ------------------------------------------------------------------ */

function createSplash() {
  if (splashWindow && !splashWindow.isDestroyed()) return;
  splashWindow = new BrowserWindow({
    width: 560,
    height: 320,
    frame: false,
    resizable: false,
    movable: true,
    show: true,
    center: true,
    alwaysOnTop: true,
    skipTaskbar: false,
    backgroundColor: "#05070c",
    icon: path.join(__dirname, fs.existsSync(path.join(__dirname, "icon.png")) ? "icon.png" : "icon.ico"),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  splashWindow.removeMenu();
  splashWindow.loadFile(path.join(__dirname, "splash.html"));
  splashWindow.on("closed", () => {
    splashWindow = null;
  });
}

function setSplash(percent, text) {
  if (!splashWindow || splashWindow.isDestroyed()) return;
  const pct = JSON.stringify(Number(percent) || 0);
  const message = JSON.stringify(String(text || ""));
  splashWindow.webContents
    .executeJavaScript(
      "window.setProgress && window.setProgress(" + pct + "," + message + ");",
      true,
    )
    .catch(() => {});
}

function closeSplash() {
  if (splashWindow && !splashWindow.isDestroyed()) {
    try {
      splashWindow.close();
    } catch {
      /* noop */
    }
  }
  splashWindow = null;
}

/* ------------------------------------------------------------------ */
/* Ventana y bandeja                                                   */
/* ------------------------------------------------------------------ */

function windowUrl() {
  return "http://127.0.0.1:" + currentPort + "/?platform=pc";
}

function createWindow() {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.show();
    mainWindow.focus();
    return;
  }

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 720,
    minWidth: 960,
    minHeight: 600,
    title: APP_NAME,
    backgroundColor: "#070B13",
    icon: path.join(__dirname, fs.existsSync(path.join(__dirname, "icon.png")) ? "icon.png" : "icon.ico"),
    show: false,
    fullscreen: true,
    autoHideMenuBar: true,
    webPreferences: {
      backgroundThrottling: false,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  Menu.setApplicationMenu(null);
  mainWindow.removeMenu();
  mainWindow.loadURL(windowUrl());

  mainWindow.once("ready-to-show", () => {
    if (!mainWindow || mainWindow.isDestroyed()) return;
    setSplash(100, "Listo. Abriendo Vision+…");
    setTimeout(() => {
      closeSplash();
      if (!mainWindow || mainWindow.isDestroyed()) return;
      if (!mainWindow.isFullScreen()) mainWindow.setFullScreen(true);
      mainWindow.show();
      mainWindow.focus();
    }, 450);
  });

  mainWindow.on("close", (event) => {
    if (quitting || !tray) return;
    event.preventDefault();
    mainWindow.hide();
    log("ventana oculta a la bandeja");
    if (tray && typeof tray.displayBalloon === "function") {
      try {
        tray.displayBalloon({
          title: "Vision+ sigue reproduciendo",
          content: "La ventana se ocultó a la bandeja. Haz clic en el icono para volver.",
          iconType: "info",
        });
      } catch {
        /* noop */
      }
    }
  });

  mainWindow.on("minimize", () => {
    if (!quitting && tray) mainWindow.hide();
  });

  mainWindow.on("leave-full-screen", () => {
    if (allowFullscreenExit) {
      allowFullscreenExit = false;
      log("pantalla completa desactivada por Ctrl+F");
      return;
    }
    if (!quitting) {
      setTimeout(() => {
        if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isFullScreen()) {
          mainWindow.setFullScreen(true);
        }
      }, 40);
    }
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  // Ctrl+F es el único atajo que entra/sale de la pantalla completa nativa.
  // Escape NO se toca aquí: sigue llegando a la interfaz como «Atrás».
  mainWindow.webContents.on("before-input-event", (event, input) => {
    if (!input || input.type !== "keyDown" || input.isAutoRepeat) return;
    const key = String(input.key || "").toLowerCase();
    if ((input.control || input.meta) && !input.alt && key === "f") {
      event.preventDefault();
      toggleFullscreen();
    }
  });
  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (url.indexOf("http://127.0.0.1:" + currentPort) !== 0) {
      event.preventDefault();
    }
  });

  mainWindow.webContents.on("render-process-gone", (event, details) => {
    log("render-process-gone: " + JSON.stringify(details));
    if (!quitting) {
      dialog.showErrorBox(
        "Vision+ PC",
        "La ventana de Vision+ se cerró inesperadamente.\n\nDetalle: " +
          (details && details.reason ? details.reason : "desconocido"),
      );
    }
  });
}

function showWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    createWindow();
    return;
  }
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

function toggleFullscreen() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  showWindow();
  const next = !mainWindow.isFullScreen();
  // Solo este atajo puede salir; si el sistema sale por otra vía, se reentra.
  allowFullscreenExit = !next;
  mainWindow.setFullScreen(next);
}

function createTray() {
  let image = nativeImage.createEmpty();
  try {
    const pngPath = path.join(__dirname, "icon.png");
    const icoPath = path.join(__dirname, "icon.ico");
    if (fs.existsSync(pngPath)) image = nativeImage.createFromPath(pngPath);
    else if (fs.existsSync(icoPath)) image = nativeImage.createFromPath(icoPath);
  } catch {
    /* noop */
  }

  tray = new Tray(image);
  tray.setToolTip(APP_NAME);
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: "Abrir Vision+", click: showWindow },
      { label: "Alternar pantalla completa (Ctrl+F)", click: toggleFullscreen },
      { label: "Recargar", click: () => mainWindow && mainWindow.reload() },
      { type: "separator" },
      { label: "Abrir carpeta de datos", click: () => shell.openPath(dataDir) },
      { type: "separator" },
      { label: "Cerrar aplicación", click: quitApp },
    ]),
  );

  tray.on("click", showWindow);
  tray.on("double-click", showWindow);
}

function stopMedia() {
  try {
    if (serverRuntime && typeof serverRuntime.abortMedia === "function") {
      serverRuntime.abortMedia();
    }
  } catch {
    /* noop */
  }
}

function quitApp() {
  quitting = true;
  log("cerrando Vision+ PC");
  stopMedia();
  closeSplash();
  try {
    if (tray) {
      tray.destroy();
      tray = null;
    }
  } catch {
    /* noop */
  }
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.destroy();
  }
  app.quit();
  setTimeout(() => app.exit(0), 1200).unref();
}

/* ------------------------------------------------------------------ */
/* Arranque                                                            */
/* ------------------------------------------------------------------ */

function bootstrap() {
  app.setAppUserModelId("com.chronosbvrx.visionplus.pc");
  log("inicio; datos en " + dataDir);
  try {
    updater.init({
      dataDir: dataDir,
      log: log,
      getWindow: () => mainWindow,
      quit: quitApp,
    });
  } catch (error) {
    log("no se pudo iniciar el actualizador: " + error.message);
  }

  createSplash();
  setSplash(6, "Iniciando Vision+ PC…");
  let splashProgress = 6;
  const splashTimer = setInterval(() => {
    splashProgress = Math.min(62, splashProgress + 2.5);
    setSplash(splashProgress, splashProgress < 25 ? "Iniciando motor local…" : "Preparando catálogo y reproductor…");
  }, 650);

  startServer()
    .then((port) => {
      clearInterval(splashTimer);
      setSplash(70, "Comprobando el motor local…");
      return ping(port, 4).then((ok) => ({ port, ok }));
    })
    .then((result) => {
      if (!result.ok) throw new Error("El servidor local no responde en /ping.");
      currentPort = result.port;
      setSplash(82, "Cargando la interfaz de Smart TV…");
      createWindow();
      createTray();
    })
    .catch((error) => {
      clearInterval(splashTimer);
      log("no se pudo iniciar: " + (error && error.stack ? error.stack : error));
      closeSplash();
      dialog.showErrorBox(
        "Vision+ PC",
        "No se pudo iniciar Vision+.\n\n" +
          (error && error.message ? error.message : String(error)) +
          "\n\nRevisa la carpeta de datos (launcher.log / server.log).",
      );
      quitApp();
    });
}

app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");
app.commandLine.appendSwitch("disable-features", "Translate,TranslateUI");
app.commandLine.appendSwitch("disable-background-timer-throttling");
app.commandLine.appendSwitch("disable-renderer-backgrounding");
app.commandLine.appendSwitch("disable-backgrounding-occluded-windows");

prepareDataDir();

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on("second-instance", showWindow);
  app.on("activate", showWindow);

  app.on("before-quit", () => {
    quitting = true;
    stopMedia();
  });

  app.on("window-all-closed", () => {
    // La app vive en la bandeja; no cerrar al ocultar la ventana.
    if (!tray) app.quit();
  });

  app.whenReady().then(bootstrap).catch((error) => {
    try {
      dialog.showErrorBox("Vision+ PC", String(error));
    } catch {
      /* noop */
    }
    app.quit();
  });
}
