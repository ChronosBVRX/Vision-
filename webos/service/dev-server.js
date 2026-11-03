"use strict";

/**
 * Servidor de desarrollo: expone el mismo `core.js` por HTTP y sirve la app
 * web, para probar todo en un navegador antes de empaquetar el .ipk.
 *
 *   node webos/service/dev-server.js
 *   abrir http://127.0.0.1:8910/
 */

const fs = require("fs");
const http = require("http");
const path = require("path");
const urlmod = require("url");
const core = require("./core");

const PORT = Number(process.env.PORT || 8910);
const APP_DIR = path.resolve(__dirname, "..", "app");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
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
    "Cache-Control": "no-store",
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
  const relative = decodeURIComponent(pathname === "/" ? "/index.html" : pathname);
  const target = path.resolve(APP_DIR, `.${relative}`);
  if (!target.startsWith(APP_DIR + path.sep) && target !== APP_DIR) {
    res.writeHead(403);
    res.end("forbidden");
    return;
  }
  fs.readFile(target, (error, data) => {
    if (error) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("not found");
      return;
    }
    res.writeHead(200, {
      "Content-Type": MIME[path.extname(target).toLowerCase()] || "application/octet-stream",
      "Cache-Control": "no-cache",
    });
    res.end(data);
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

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    cors(res);
    res.writeHead(204);
    res.end();
    return;
  }

  const parsed = urlmod.parse(req.url, true);
  const method = String(parsed.pathname || "/").replace(/^\/+/, "");
  const handler = methods[method];
  if (handler) {
    try {
      const params = req.method === "POST" ? await readBody(req) : { ...parsed.query };
      const payload = await handler(params);
      sendJson(res, 200, { returnValue: true, ...payload });
    } catch (error) {
      console.error(`[dev] ${method}:`, error.message);
      sendJson(res, 200, {
        returnValue: false,
        errorText: error.message,
        errorCode: error.portalCode || 1,
      });
    }
    return;
  }

  staticFile(req, res, parsed.pathname || "/");
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`Vision+ webOS dev server: http://127.0.0.1:${PORT}/`);
});
