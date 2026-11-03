"use strict";

/**
 * Resuelve los enlaces `token_api` de GeeSports.
 *
 * La fuente no publica la URL del canal: entrega una página/endpoint que arranca
 * su reproductor y recién ahí escribe la dirección del HLS/DASH. El resolver
 * pide esa página con las mismas cabeceras del reproductor y extrae la primera
 * URL de media. Es el equivalente en Node del WebView oculto de la app Android.
 */

const http = require("http");
const https = require("https");
const urlmod = require("url");

const TTL_MS = 5 * 60 * 1000;
const cache = new Map();

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
  "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

function parseEmbed(tokenApi) {
  try {
    const data = JSON.parse(String(tokenApi || ""));
    if (!data || typeof data !== "object") return null;
    const api = String(data.api || data.url || "").trim();
    if (!/^https?:\/\//i.test(api)) return null;
    return {
      api,
      type: String(data.type || "embed").trim().toLowerCase(),
      linkKey: String(data.link_key || "playback_url").trim(),
    };
  } catch (error) {
    if (/^https?:\/\//i.test(String(tokenApi || "").trim())) {
      return { api: String(tokenApi).trim(), type: "embed", linkKey: "playback_url" };
    }
    return null;
  }
}

function headersFor(api) {
  let origin = "";
  try {
    const parsed = urlmod.parse(api);
    origin = `${parsed.protocol}//${parsed.host}`;
  } catch (error) {
    origin = "";
  }
  return {
    "user-agent": UA,
    referer: api,
    origin,
  };
}

function requestText(target, headers, timeout, redirectsLeft) {
  return new Promise((resolve, reject) => {
    let parsed;
    try {
      parsed = urlmod.parse(target);
    } catch (error) {
      reject(error);
      return;
    }
    if (!/^https?:$/.test(parsed.protocol || "")) {
      reject(new Error("dirección embed no válida"));
      return;
    }
    const transport = parsed.protocol === "http:" ? http : https;
    const request = transport.request(
      {
        hostname: parsed.hostname,
        port: parsed.port || (parsed.protocol === "http:" ? 80 : 443),
        path: parsed.path,
        method: "GET",
        headers,
        rejectUnauthorized: false,
      },
      (response) => {
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location && redirectsLeft > 0) {
          response.resume();
          const next = urlmod.resolve(target, response.headers.location);
          resolve(requestText(next, headers, timeout, redirectsLeft - 1));
          return;
        }
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          resolve({
            status: response.statusCode,
            url: target,
            body: Buffer.concat(chunks).toString("utf8"),
            contentType: String(response.headers["content-type"] || ""),
          });
        });
      },
    );
    request.setTimeout(timeout || 15000, () => request.destroy(new Error("embed timeout")));
    request.on("error", reject);
    request.end();
  });
}

function normalizeMediaUrl(raw, base) {
  let value = String(raw || "").trim();
  if (!value) return "";
  value = value
    .replace(/\\\//g, "/")
    .replace(/&amp;/g, "&")
    .replace(/\\u0026/g, "&")
    .replace(/^['"]|['"]$/g, "");
  if (!value) return "";
  if (/%3A|%2F|%3F|%26/i.test(value)) {
    try {
      const decoded = decodeURIComponent(value);
      if (decoded && decoded !== value) value = decoded;
    } catch (error) {
      /* se conserva el valor original */
    }
  }
  if (value.startsWith("//")) value = `https:${value}`;
  if (!/^https?:\/\//i.test(value)) {
    try {
      value = urlmod.resolve(base || "", value);
    } catch (error) {
      return "";
    }
  }
  if (!/\.m3u8(\?|$)|\.mpd(\?|$)|\.ts(\?|$)|\.mp4(\?|$)/i.test(value)) return "";
  return value;
}

function findMedia(body, base) {
  const text = String(body || "");
  const patterns = [
    /["'](?:playbackURL|playback_url|playbackUrl|file|source|src|url)["']\s*[:=]\s*["']([^"']+)["']/gi,
    /(?:playbackURL|playback_url|playbackUrl|file|source|src)\s*[:=]\s*["']([^"']+)["']/gi,
    /https?:\\?\/\\?\/[^\s"'<>\\]+?\.(?:m3u8|mpd|ts|mp4)(?:\?[^\s"'<>\\]*)?/gi,
  ];
  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(text))) {
      const candidate = normalizeMediaUrl(match[1] || match[0], base);
      if (candidate) return candidate;
    }
  }
  // La página puede traer la URL en JSON escapado o como parámetro de otra página.
  try {
    const json = JSON.parse(text);
    const queue = [json];
    while (queue.length) {
      const value = queue.shift();
      if (typeof value === "string") {
        const found = normalizeMediaUrl(value, base);
        if (found) return found;
      } else if (Array.isArray(value)) {
        queue.push(...value);
      } else if (value && typeof value === "object") {
        queue.push(...Object.values(value));
      }
    }
  } catch (error) {
    /* la página no es JSON */
  }
  return "";
}

function findIframe(body) {
  const match = /<iframe[^>]+src=["']([^"']+)["']/i.exec(String(body || ""));
  return match ? match[1] : "";
}

function isMediaUrl(raw) {
  return /\.m3u8(\?|$)|(?:\?|&)[^=]+=m3u8|\/m3u8(?:\?|$)|(?:\?|&)format=m3u8|\.mpd(\?|$)|\/manifest\.mpd/i.test(String(raw || ""));
}

function browserAvailable() {
  return Boolean(process.versions && process.versions.electron) && process.env.VISION_PC === "1";
}

/**
 * Misma idea que el WebView oculto de Android: abre la página embed en una
 * ventana Electron invisible y escucha las peticiones de red. Algunos portales
 * (los que entregan la señal desde un reproductor JavaScript) no escriben la
 * URL en el HTML; solo aparecen cuando su player arranca.
 */
function resolveWithBrowser(config, headers) {
  return new Promise((resolve, reject) => {
    let electron;
    try {
      electron = require("electron");
    } catch (error) {
      reject(error);
      return;
    }
    if (!electron || !electron.BrowserWindow) {
      reject(new Error("Electron no está disponible para resolver el embed."));
      return;
    }
    let win = null;
    let isolateSession = null;
    let settled = false;
    let timer = null;
    function cleanup() {
      clearTimeout(timer);
      if (isolateSession) {
        try { isolateSession.webRequest.onBeforeRequest(null); } catch (error) { /* noop */ }
        isolateSession = null;
      }
      if (win && !win.isDestroyed()) {
        try { win.webContents.debugger.detach(); } catch (error) { /* noop */ }
        try { win.destroy(); } catch (error) { /* noop */ }
      }
      win = null;
    }
    function finish(url) {
      if (settled) return;
      const clean = String(url || "").trim();
      if (!isMediaUrl(clean)) return;
      settled = true;
      cleanup();
      resolve({ url: clean, headers });
    }
    function fail(message) {
      if (settled) return;
      settled = true;
      cleanup();
      reject(new Error(message || "La fuente no publicó la señal del canal."));
    }
    try {
      win = new electron.BrowserWindow({
        show: false,
        focusable: false,
        skipTaskbar: true,
        width: 900,
        height: 600,
        backgroundColor: "#000000",
        webPreferences: {
          backgroundThrottling: false,
          contextIsolation: true,
          nodeIntegration: false,
          sandbox: true,
          partition: "vp-embed-" + Date.now() + "-" + Math.random().toString(36).slice(2),
          autoplayPolicy: "no-user-gesture-required",
        },
      });
      isolateSession = win.webContents.session;
      isolateSession.webRequest.onBeforeRequest({ urls: ["*://*/*"] }, function (details, callback) {
        try {
          if (details && details.url) finish(details.url);
        } finally {
          if (typeof callback === "function") callback({});
        }
      });
      win.webContents.setAudioMuted(true);
      win.webContents.setWindowOpenHandler(function () { return { action: "deny" }; });
      win.webContents.debugger.attach("1.3");
      win.webContents.debugger.on("message", function (event, method, params) {
        if (method === "Network.requestWillBeSent" && params && params.request) {
          finish(params.request.url);
        } else if (method === "Network.responseReceived" && params && params.response) {
          finish(params.response.url);
        } else if (method === "Network.loadingFinished" && params && params.requestId) {
          /* la petición ya se vio en requestWillBeSent */
        }
      });
      win.webContents.debugger.sendCommand("Network.enable").catch(function () {});
      win.webContents.on("did-finish-load", function () {
        // Algunos players esperan un gesto o un botón de play. Se intenta sin
        // ruido; si la página ya arrancó sola, esto no cambia nada.
        win.webContents.executeJavaScript(`(function(){
          try {
            var candidates = document.querySelectorAll('video,button,[class*=play],[id*=play],[class*=Play]');
            for (var i = 0; i < candidates.length; i++) { try { candidates[i].click(); } catch (e) {} }
            var videos = document.querySelectorAll('video');
            for (var j = 0; j < videos.length; j++) {
              try { videos[j].muted = true; videos[j].play && videos[j].play().catch(function(){}); } catch (e) {}
            }
          } catch (e) {}
        })();`, true).catch(function () {});
      });
      win.loadURL(config.api, {
        userAgent: headers["user-agent"],
        extraHeaders: "Referer: " + headers.referer + "\nOrigin: " + headers.origin + "\n",
      }).catch(function () { /* la página puede abortar una navegación hija; manda la captura o el timeout */ });
      timer = setTimeout(function () { fail("La fuente tardó demasiado en publicar la señal."); }, 20000);
    } catch (error) {
      fail(error.message);
    }
  });
}

function resolveViaHttp(config, headers) {
  return requestText(config.api, headers, 8000, 3)
    .then((response) => {
      let media = findMedia(response.body, response.url);
      if (!media) {
        const iframe = findIframe(response.body);
        if (iframe) {
          const iframeUrl = /^https?:\/\//i.test(iframe) ? iframe : urlmod.resolve(response.url, iframe);
          const next = normalizeMediaUrl(iframeUrl, response.url);
          if (next) {
            media = next;
          } else {
            return requestText(iframeUrl, headers, 8000, 2).then((frameResponse) => {
              const found = findMedia(frameResponse.body, frameResponse.url);
              if (!found) return "";
              return found;
            });
          }
        }
      }
      return media || "";
    });
}

function resolve(tokenApi) {
  const config = parseEmbed(tokenApi);
  if (!config) return Promise.reject(new Error("El enlace embed no trae una dirección válida."));
  const cached = cache.get(config.api);
  if (cached && Date.now() - cached.at < TTL_MS) return Promise.resolve(cached.value);
  const headers = headersFor(config.api);
  return resolveViaHttp(config, headers)
    .catch(function () { return ""; })
    .then((media) => {
      if (media) return { url: media, headers };
      if (browserAvailable()) return resolveWithBrowser(config, headers);
      throw new Error("La fuente no publicó la señal del canal.");
    })
    .then((value) => {
      cache.set(config.api, { at: Date.now(), value });
      return value;
    });
}

module.exports = {
  resolve,
  parseEmbed,
  findMedia,
  headersFor,
};
