"use strict";

/**
 * Vision+ PC: servidor local del port portable para Windows.
 *
 * Reutiliza EXACTAMENTE el mismo motor y la misma interfaz del port de Smart TV
 * (webos/app + webos/service). Solo añade la capa de escritorio:
 *
 *   - sirve la app por HTTP en 127.0.0.1 (el navegador no necesita Luna);
 *   - activa el modo PC del motor (TS/HEVC y HLS en vivo por FFmpeg -> MP4 H.264);
 *   - publica /ping para que el lanzador sepa cuándo está listo;
 *   - escribe data/port.json y permite apagado ordenado desde el lanzador.
 *
 * En el paquete final la estructura es:
 *   VisionPlusPC/
 *     VisionPlusPC.exe
 *     server.js
 *     app/            <- webos/app copiada
 *     service/        <- webos/service copiada (sin el ffmpeg ARM)
 *     runtime/        <- node.exe + ffmpeg.exe
 *     data/           <- sesión, caché, perfil del navegador y logs
 */

const fs = require("fs");
const http = require("http");
const path = require("path");
const urlmod = require("url");

const HOST = "127.0.0.1";
const DEFAULT_PORT = Number(process.env.VISION_PORT || process.env.PORT || 8910) || 8910;
const MAX_PORT_ATTEMPTS = 20;

const BASE_DIR = __dirname;
const APP_DIR = path.resolve(process.env.VISION_APP_DIR || path.join(BASE_DIR, "app"));
const SERVICE_DIR = path.resolve(process.env.VISION_SERVICE_DIR || path.join(BASE_DIR, "service"));
const DATA_DIR = path.resolve(process.env.VISION_DATA_DIR || path.join(BASE_DIR, "data"));
const PORT_FILE = path.join(DATA_DIR, "port.json");
const LOG_FILE = path.join(DATA_DIR, "server.log");
const SHUTDOWN_TOKEN = process.env.VISION_SHUTDOWN_TOKEN || "";

process.env.VISION_PC = "1";
process.env.VISION_DATA_DIR = process.env.VISION_DATA_DIR || DATA_DIR;
process.env.VISION_WEBOS_SESSION =
  process.env.VISION_WEBOS_SESSION || path.join(DATA_DIR, "session.json");

function exists(file) {
  try {
    return fs.statSync(file).isFile();
  } catch {
    return false;
  }
}

// El relay de FFmpeg es la pieza que hace reproducible el catálogo en Chrome/Edge:
// MPEG-TS y HEVC no se pueden reproducir de forma nativa en un navegador de PC.
if (!process.env.VISION_FFMPEG) {
  const candidates = [
    path.join(BASE_DIR, "runtime", process.platform === "win32" ? "ffmpeg.exe" : "ffmpeg"),
    path.join(BASE_DIR, "runtime", "ffmpeg.exe"),
    path.join(BASE_DIR, "runtime", "ffmpeg"),
    path.join(BASE_DIR, "..", "runtime", "ffmpeg.exe"),
    path.join(BASE_DIR, "..", "runtime", "ffmpeg"),
  ];
  for (const candidate of candidates) {
    if (exists(candidate)) {
      process.env.VISION_FFMPEG = path.resolve(candidate);
      break;
    }
  }
  if (!process.env.VISION_FFMPEG) {
    // Último recurso: un FFmpeg instalado en el PATH del sistema.
    process.env.VISION_FFMPEG = process.platform === "win32" ? "ffmpeg.exe" : "ffmpeg";
  }
}

if (!exists(path.join(SERVICE_DIR, "core.js"))) {
  console.error(`[pc] No se encontró el motor en ${SERVICE_DIR}`);
  process.exit(1);
}

fs.mkdirSync(DATA_DIR, { recursive: true });

function appendLog(line) {
  try {
    fs.appendFileSync(LOG_FILE, `${new Date().toISOString()} ${line}\n`);
  } catch {
    /* el lanzador ya redirige stdout; el archivo es un extra */
  }
}

const originalLog = console.log.bind(console);
const originalError = console.error.bind(console);
console.log = (...args) => {
  const line = args.join(" ");
  originalLog(line);
  appendLog(line);
};
console.error = (...args) => {
  const line = args.join(" ");
  originalError(line);
  appendLog(`ERROR ${line}`);
};

const core = require(path.join(SERVICE_DIR, "core"));

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".mp4": "video/mp4",
  ".ts": "video/mp2t",
  ".m3u8": "application/vnd.apple.mpegurl",
  ".srt": "text/plain; charset=utf-8",
  ".vtt": "text/vtt; charset=utf-8",
};

function cors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
}

function sendJson(res, status, payload) {
  cors(res);
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
    "Cache-Control": "no-store, no-cache, must-revalidate",
  });
  res.end(body);
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    return {};
  }
}

function staticFile(req, res, pathname) {
  let relative;
  try {
    relative = decodeURIComponent(pathname === "/" ? "/index.html" : pathname);
  } catch {
    res.writeHead(400, { "Content-Type": "text/plain" });
    return res.end("bad request");
  }
  const target = path.resolve(APP_DIR, `.${relative}`);
  if (!target.startsWith(APP_DIR + path.sep) && target !== APP_DIR) {
    res.writeHead(403, { "Content-Type": "text/plain" });
    return res.end("forbidden");
  }
  fs.stat(target, (error, stat) => {
    if (error || !stat.isFile()) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      return res.end("not found");
    }
    res.writeHead(200, {
      "Content-Type": MIME[path.extname(target).toLowerCase()] || "application/octet-stream",
      "Content-Length": stat.size,
      "Cache-Control": "no-cache",
    });
    if (req.method === "HEAD") return res.end();
    const stream = fs.createReadStream(target);
    stream.on("error", () => {
      try {
        res.destroy();
      } catch {
        /* noop */
      }
    });
    stream.pipe(res);
  });
}

const methods = {
  ping: () => core.ping(),
  home: (params) => core.home(params),
  banners: (params) => core.banners(params),
  search: (params) => core.search(params),
  category: (params) => core.category(params),
  section: (params) => core.section(params),
  live: (params) => core.live(params),
  liveCatalog: () => core.liveCatalog(),
  epgBulk: (params) => core.epgBulk(params),
  events: (params) => core.events(params),
  sports: (params) => core.sports(params),
  matchStats: (params) => core.matchStats(params),
  resolveEmbed: (params) => core.resolveEmbed(params),
  markers: (params) => core.markers(params),
  episodes: (params) => core.episodes(params),
  resolve: (params) => core.resolve(params),
  resolveLive: (params) => core.resolveLive(params),
  mediaInfo: (params) => core.mediaInfo(params),
  prewarm: () => core.prewarm(),
  abortMedia: () => core.abortMedia(),
  resetSession: () => core.resetSession(),
  clearCache: () => core.clearCache(),
};

let currentPort = 0;
let currentServer = null;

function writePortFile(port) {
  try {
    fs.writeFileSync(
      PORT_FILE,
      JSON.stringify(
        {
          app: "Vision+ PC",
          pid: process.pid,
          port,
          url: `http://${HOST}:${port}/?platform=pc`,
          startedAt: new Date().toISOString(),
        },
        null,
        2,
      ),
    );
  } catch (error) {
    console.error("[pc] no se pudo escribir port.json:", error.message);
  }
}

function removePortFile() {
  try {
    const raw = fs.readFileSync(PORT_FILE, "utf8");
    const info = JSON.parse(raw);
    if (Number(info.pid) === process.pid) fs.unlinkSync(PORT_FILE);
  } catch {
    /* noop */
  }
}

function createServer() {
  return http.createServer(async (req, res) => {
    if (req.method === "OPTIONS") {
      cors(res);
      res.writeHead(204);
      res.end();
      return;
    }

    const parsed = urlmod.parse(req.url, true);
    const pathname = parsed.pathname || "/";

    if (pathname === "/favicon.ico") {
      const icon = path.join(APP_DIR, "images", "icon.ico");
      return fs.stat(icon, (error, stat) => {
        if (error || !stat.isFile()) {
          res.writeHead(404);
          return res.end();
        }
        res.writeHead(200, {
          "Content-Type": "image/x-icon",
          "Content-Length": stat.size,
          "Cache-Control": "public, max-age=86400",
        });
        fs.createReadStream(icon).pipe(res);
      });
    }

    if (pathname === "/healthz") {
      return sendJson(res, 200, {
        status: "ok",
        app: "Vision+ PC",
        port: currentPort,
        pid: process.pid,
        uptime: Math.round(process.uptime()),
      });
    }

    if (pathname === "/shutdown") {
      if (SHUTDOWN_TOKEN && req.headers["x-vision-token"] !== SHUTDOWN_TOKEN) {
        return sendJson(res, 403, { returnValue: false, errorText: "forbidden" });
      }
      sendJson(res, 200, { returnValue: true, closing: true });
      setTimeout(() => {
        try {
          core.abortMedia();
        } catch {
          /* noop */
        }
        try {
          if (currentServer) currentServer.close();
        } catch {
          /* noop */
        }
        removePortFile();
        process.exit(0);
      }, 120);
      return;
    }

    const method = String(pathname).replace(/^\/+/, "");
    const handler = methods[method];
    if (handler) {
      try {
        const params = req.method === "POST" ? await readBody(req) : { ...parsed.query };
        if (method === "ping") {
          const payload = await handler(params);
          payload.pc = true;
          payload.platform = "windows";
          payload.app = "Vision+ PC";
          payload.port = currentPort;
          payload.url = `http://${HOST}:${currentPort}/?platform=pc`;
          return sendJson(res, 200, { returnValue: true, ...payload });
        }
        const payload = await handler(params);
        sendJson(res, 200, { returnValue: true, ...payload });
      } catch (error) {
        console.error(`[pc] ${method}:`, error.message);
        sendJson(res, 200, {
          returnValue: false,
          errorText: error.message,
          errorCode: error.portalCode || 1,
        });
      }
      return;
    }

    staticFile(req, res, pathname);
  });
}

function listen(server, port, attemptsLeft) {
  server.once("error", (error) => {
    if (error.code === "EADDRINUSE" && attemptsLeft > 0) {
      console.log(`[pc] puerto ${port} ocupado; probando ${port + 1}`);
      listen(server, port + 1, attemptsLeft - 1);
      return;
    }
    console.error("[pc] no se pudo abrir el servidor:", error.message);
    process.exit(1);
  });
  server.listen(port, HOST, () => {
    currentPort = port;
    writePortFile(port);
    console.log(`[pc] Vision+ PC listo en http://${HOST}:${port}/?platform=pc`);
    console.log(`VISION_PC_READY ${port}`);
    setTimeout(() => {
      core.prewarm().catch(() => {});
    }, 120);
  });
}

currentServer = createServer();
listen(currentServer, DEFAULT_PORT, MAX_PORT_ATTEMPTS);

function shutdown() {
  removePortFile();
  try {
    core.abortMedia();
  } catch {
    /* noop */
  }
  try {
    if (currentServer) currentServer.close();
  } catch {
    /* noop */
  }
  setTimeout(() => process.exit(0), 80).unref();
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
process.on("exit", removePortFile);

module.exports = {
  core,
  abortMedia: () => core.abortMedia(),
  shutdown,
};
